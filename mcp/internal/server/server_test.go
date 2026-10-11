package server

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/fssrepository/myscoutee-mcp/internal/api"
	"github.com/modelcontextprotocol/go-sdk/mcp"
)

func fixture(t *testing.T) (*api.Client, *atomic.Int32) {
	t.Helper()
	version := &atomic.Int32{}
	version.Store(2)
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/help/active" {
			http.NotFound(w, r)
			return
		}
		if r.Header.Get("Authorization") != "" || r.Header.Get("X-MyScoutee-Client-Id") != "" {
			t.Error("private credentials leaked to help")
		}
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprintf(w, `{"activeRevision":{"id":"help-%d","version":%d,"lang":%q,"active":true,"title":"Help","sections":[{"id":"groups","title":"Csoportok / Groups","blurb":"Profiles","contentHtml":"<p>Use the selector.</p><ul><li>Keep permissions.</li></ul><script>SECRET</script>"},{"id":"assets","title":"Assets","blurb":"Base profile","contentHtml":"<p>Global discovery.</p>"}]},"auditTrail":[{"private":"must not appear"}]}`, version.Load(), version.Load(), r.URL.Query().Get("lang"))
	}))
	t.Cleanup(upstream.Close)
	client, err := api.NewClient(upstream.URL, "", "")
	if err != nil {
		t.Fatal(err)
	}
	return client, version
}
func TestHelpUsesActiveRevisionAndSanitizedStructuredSections(t *testing.T) {
	client, version := fixture(t)
	out, err := ReadHelp(context.Background(), client, "hu", "groups", "", true)
	if err != nil {
		t.Fatal(err)
	}
	if len(out.Sections) != 1 || out.Language != "hu" || strings.Contains(out.Sections[0].Text, "SECRET") || !strings.Contains(out.Sections[0].Text, "• Keep permissions.") {
		t.Fatalf("unexpected help: %+v", out)
	}
	version.Store(3)
	newer, err := ReadHelp(context.Background(), client, "en", "", "", false)
	if err != nil || newer.Version != 3 || newer.Sections[0].Text != "" {
		t.Fatalf("stale/full catalog: %+v %v", newer, err)
	}
	result, err := ReadHelp(context.Background(), client, "hu", "", "csoport", false)
	if err != nil || len(result.Sections) != 1 {
		t.Fatalf("search: %+v %v", result, err)
	}
	empty, err := ReadHelp(context.Background(), client, "en", "", "no match", false)
	if err != nil || len(empty.Sections) != 0 {
		t.Fatal("empty search must remain empty")
	}
	for _, item := range []struct{ lang, topic, query string }{{"de", "", ""}, {"hu", "missing", ""}, {"en", "", strings.Repeat("a", 201)}} {
		if _, err := ReadHelp(context.Background(), client, item.lang, item.topic, item.query, false); err == nil {
			t.Fatal("invalid help input accepted")
		}
	}
}
func TestMCPHTTPDiscoveryCallResourceAndNoPrivateTools(t *testing.T) {
	client, _ := fixture(t)
	handler, err := PublicHandler(client)
	if err != nil {
		t.Fatal(err)
	}
	remote := httptest.NewServer(handler)
	defer remote.Close()
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	session, err := mcp.NewClient(&mcp.Implementation{Name: "test", Version: "1"}, nil).Connect(ctx, &mcp.StreamableClientTransport{Endpoint: remote.URL + "/mcp"}, nil)
	if err != nil {
		t.Fatal(err)
	}
	defer session.Close()
	tools, err := session.ListTools(ctx, nil)
	if err != nil || len(tools.Tools) != 3 {
		t.Fatalf("public tools: %+v %v", tools, err)
	}
	if !strings.Contains(session.InitializeResult().Instructions, "Anonymous help is available without login or an API key") {
		t.Fatal("initialization does not explain anonymous help")
	}
	for _, tool := range tools.Tools {
		if tool.Annotations == nil || !tool.Annotations.ReadOnlyHint || tool.Annotations.DestructiveHint == nil || *tool.Annotations.DestructiveHint || tool.Annotations.OpenWorldHint == nil || *tool.Annotations.OpenWorldHint {
			t.Fatalf("help annotations must explicitly describe a bounded read: %s", tool.Name)
		}
		if !strings.Contains(tool.Description, "Available anonymously; no login or API key required") {
			t.Fatalf("discovery omits anonymous access for %s", tool.Name)
		}
	}
	resources, err := session.ListResources(ctx, nil)
	if err != nil || len(resources.Resources) != 2 {
		t.Fatalf("help resources: %+v %v", resources, err)
	}
	for _, resource := range resources.Resources {
		if !strings.Contains(resource.Description, "Available anonymously; no login or API key required") {
			t.Fatalf("discovery omits anonymous access for %s", resource.URI)
		}
	}
	result, err := session.CallTool(ctx, &mcp.CallToolParams{Name: "read_help_topic", Arguments: map[string]any{"language": "hu", "topic": "groups"}})
	if err != nil || result.IsError {
		t.Fatalf("call: %+v %v", result, err)
	}
	raw, _ := json.Marshal(result.StructuredContent)
	if !strings.Contains(string(raw), "revisionId") || strings.Contains(string(raw), "must not appear") {
		t.Fatalf("bad result %s", raw)
	}
	res, err := session.ReadResource(ctx, &mcp.ReadResourceParams{URI: "template://help/en"})
	if err != nil || len(res.Contents) != 1 || !strings.Contains(res.Contents[0].Text, "Global discovery.") {
		t.Fatalf("resource: %+v %v", res, err)
	}
	_, err = session.CallTool(ctx, &mcp.CallToolParams{Name: "createEvents", Arguments: map[string]any{}})
	if err == nil {
		t.Fatal("private operation exposed")
	}
	client.Token = "private"
	if _, err = PublicHandler(client); err == nil {
		t.Fatal("HTTP must reject shared API identity")
	}
}
func TestMissingRevisionAndHTTPErrorDoNotFallback(t *testing.T) {
	for _, body := range []string{`{"activeRevision":null}`, `{"activeRevision":{"id":"x","version":1,"active":false,"lang":"en"}}`, `not json`} {
		t.Run(body, func(t *testing.T) {
			upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) { fmt.Fprint(w, body) }))
			defer upstream.Close()
			client, _ := api.NewClient(upstream.URL, "", "")
			if _, err := ReadHelp(context.Background(), client, "en", "", "", true); err == nil {
				t.Fatal("invalid revision accepted")
			}
		})
	}
}
func TestRemoteOAuthIsolationRevocationAndAnonymousHelp(t *testing.T) {
	var revoked atomic.Bool
	var handshakes atomic.Int32
	tokenA, tokenB := "mca_"+strings.Repeat("a", 43), "mca_"+strings.Repeat("b", 43)
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/help/active" {
			if r.Header.Get("Authorization") != "" {
				t.Error("help received credential")
			}
			fmt.Fprint(w, `{"activeRevision":{"id":"h","version":2,"lang":"en","active":true,"sections":[]}}`)
			return
		}
		if r.Header.Get("X-MyScoutee-Client-Id") != "" {
			t.Error("OAuth must not invent a client header")
		}
		token := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
		if token != tokenA && token != tokenB || token == tokenA && revoked.Load() {
			w.WriteHeader(401)
			return
		}
		if r.URL.Path == "/integrations/v1/connect" {
			handshakes.Add(1)
			if token == tokenA {
				fmt.Fprint(w, `{"connected":true,"profileId":"alice","groupId":"team","operations":["chatDetails"],"inviteGroups":[]}`)
			} else {
				fmt.Fprint(w, `{"connected":true,"profileId":"bob","operations":[],"inviteGroups":[]}`)
			}
			return
		}
		if token != tokenA {
			t.Error("unauthorized mutation reached backend")
		}
		fmt.Fprint(w, `{"id":"alice-chat"}`)
	}))
	defer upstream.Close()
	apiClient, _ := api.NewClient(upstream.URL, "", "")
	apiClient.PublicBase = "https://myscoutee.example/api"
	handler, err := PublicHandler(apiClient)
	if err != nil {
		t.Fatal(err)
	}
	remote := httptest.NewServer(handler)
	defer remote.Close()
	call := func(token, method, args string) (int, string, http.Header) {
		req, _ := http.NewRequest("POST", remote.URL+"/mcp/private", strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"`+method+`","params":`+args+`}`))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Accept", "application/json, text/event-stream")
		req.Header.Set("MCP-Protocol-Version", "2025-11-25")
		if token != "" {
			req.Header.Set("Authorization", "Bearer "+token)
		}
		res, e := http.DefaultClient.Do(req)
		if e != nil {
			t.Fatal(e)
		}
		defer res.Body.Close()
		body, _ := io.ReadAll(res.Body)
		return res.StatusCode, string(body), res.Header
	}
	for _, token := range []string{"", "msc_api_key", "mca_invalid"} {
		code, _, h := call(token, "tools/list", `{}`)
		if code != 401 || !strings.Contains(h.Get("WWW-Authenticate"), `https://myscoutee.example/.well-known/oauth-protected-resource/mcp/private`) {
			t.Fatal("missing OAuth challenge", code, h)
		}
	}
	for _, token := range []string{tokenA, tokenB, tokenA} {
		before := handshakes.Load()
		code, body, h := call(token, "tools/list", `{}`)
		if code != 200 || !strings.Contains(h.Get("Cache-Control"), "no-store") {
			t.Fatal(code, body)
		}
		if handshakes.Load() != before+1 {
			t.Fatal("handshake repeated or missing")
		}
		if token == tokenA && (!strings.Contains(body, `alice`) || strings.Contains(body, `bob`)) {
			t.Fatal("wrong Alice context", body)
		}
		if token == tokenB && (strings.Contains(body, `alice`) || strings.Contains(body, `"name":"chatDetails"`)) {
			t.Fatal("Alice capability leaked to Bob", body)
		}
	}
	code, body, _ := call(tokenB, "tools/call", `{"name":"chatDetails","arguments":{"id":"c"}}`)
	if code != 200 || !strings.Contains(body, `"isError":true`) {
		t.Fatal(code, body)
	}
	code, body, _ = call(tokenA, "tools/call", `{"name":"chatDetails","arguments":{"id":"c"}}`)
	if code != 200 || !strings.Contains(body, "alice-chat") {
		t.Fatal(code, body)
	}
	code, body, _ = call(tokenA, "tools/call", `{"name":"list_help_topics","arguments":{}}`)
	if code != 200 || strings.Contains(body, `"isError":true`) {
		t.Fatal(code, body)
	}
	revoked.Store(true)
	code, _, _ = call(tokenA, "tools/list", `{}`)
	if code != 401 {
		t.Fatal("revoked token accepted", code)
	}
	code, _, _ = call(tokenB, "tools/list", `{}`)
	if code != 200 {
		t.Fatal("revocation crossed identities", code)
	}
	if apiClient.Token != "" {
		t.Fatal("process identity mutated")
	}
}
