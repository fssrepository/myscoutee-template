package main

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func fixtureSlice(name, service string, ready, terminating bool) endpointSlice {
	v := endpointSlice{Metadata: sliceMeta{Name: name, Labels: map[string]string{"kubernetes.io/service-name": service}}}
	v.Endpoints = append(v.Endpoints, struct {
		Conditions struct {
			Ready       *bool `json:"ready"`
			Terminating bool  `json:"terminating"`
		} `json:"conditions"`
	}{})
	v.Endpoints[0].Conditions.Ready = &ready
	v.Endpoints[0].Conditions.Terminating = terminating
	return v
}
func TestKubernetesAggregationAndUnknown(t *testing.T) {
	s := &kubeSource{expected: map[string]bool{"a": true, "b": true}, slices: map[string]endpointSlice{}, changed: make(chan struct{})}
	check := func(ready, known bool) {
		t.Helper()
		v, _, _ := s.snapshot()
		if v.Ready != ready || v.Known != known {
			t.Fatalf("snapshot %+v", v)
		}
	}
	check(false, false)
	s.update(func() {
		s.known = true
		s.slices["a1"] = fixtureSlice("a1", "a", true, false)
		s.slices["b1"] = fixtureSlice("b1", "b", true, false)
		s.slices["a2"] = fixtureSlice("a2", "a", true, false)
	})
	check(true, true)
	_, _, unchanged := s.snapshot()
	s.update(func() { delete(s.slices, "a1") })
	check(true, true)
	select {
	case <-unchanged:
		t.Fatal("redundant pod loss changed aggregate")
	default:
	}
	s.update(func() { s.slices["a2"] = fixtureSlice("a2", "a", true, true) })
	check(false, true)
	select {
	case <-unchanged:
	default:
		t.Fatal("last serving instance loss did not notify")
	}
	s.update(func() { s.known = false })
	check(false, false)
	s.update(func() { s.known = true; s.slices["a2"] = fixtureSlice("a2", "a", true, false) })
	check(true, true)
}
func TestKubernetesListWatchExpiryAndTokenRotation(t *testing.T) {
	tokenPath := filepath.Join(t.TempDir(), "token")
	os.WriteFile(tokenPath, []byte("first"), 0600)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("watch") == "true" {
			if r.Header.Get("Authorization") != "Bearer second" {
				t.Error("token was not reloaded")
			}
			if r.URL.Query().Get("resourceVersion") != "10" {
				t.Error("list/watch gap")
			}
			fmt.Fprintln(w, `{"type":"MODIFIED","object":{"metadata":{"name":"a1","resourceVersion":"11","labels":{"kubernetes.io/service-name":"a"}},"endpoints":[{"conditions":{"ready":false}}]}}`)
			fmt.Fprintln(w, `{"type":"ERROR","object":{"code":410}}`)
		} else {
			if r.Header.Get("Authorization") != "Bearer first" {
				t.Error("missing list authentication")
			}
			fmt.Fprintln(w, `{"metadata":{"resourceVersion":"10"},"items":[{"metadata":{"name":"a1","labels":{"kubernetes.io/service-name":"a"}},"endpoints":[{"conditions":{"ready":true}}]}]}`)
		}
	}))
	defer server.Close()
	s := &kubeSource{expected: map[string]bool{"a": true}, slices: map[string]endpointSlice{}, changed: make(chan struct{}), client: server.Client(), endpoint: server.URL + "?labelSelector=x", tokenPath: tokenPath}
	if err := s.list(context.Background()); err != nil {
		t.Fatal(err)
	}
	v, _, _ := s.snapshot()
	if !v.Ready {
		t.Fatal("initial list not applied")
	}
	os.WriteFile(tokenPath, []byte("second"), 0600)
	if err := s.watch(context.Background()); err != expiredVersion {
		t.Fatalf("watch: %v", err)
	}
	v, _, _ = s.snapshot()
	if v.Ready {
		t.Fatal("watch modification not applied")
	}
}
func TestKubernetesInterruptedWatchReconcilesAndFailsClosed(t *testing.T) {
	tokenPath := filepath.Join(t.TempDir(), "token")
	os.WriteFile(tokenPath, []byte("token"), 0600)
	watchOpened := make(chan struct{}, 2)
	fail := make(chan struct{})
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.Contains(r.URL.RawQuery, "watch=true") {
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(200)
			w.(http.Flusher).Flush()
			watchOpened <- struct{}{}
			select {
			case <-fail:
			case <-r.Context().Done():
			}
		} else {
			fmt.Fprintln(w, `{"metadata":{"resourceVersion":"10"},"items":[{"metadata":{"name":"a1","labels":{"kubernetes.io/service-name":"a"}},"endpoints":[{"conditions":{"ready":true}}]}]}`)
		}
	}))
	defer server.Close()
	s := &kubeSource{expected: map[string]bool{"a": true}, slices: map[string]endpointSlice{}, changed: make(chan struct{}), client: server.Client(), endpoint: server.URL + "?selector=x", tokenPath: tokenPath}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	done := make(chan struct{})
	go func() { s.run(ctx); close(done) }()
	select {
	case <-watchOpened:
	case <-time.After(time.Second):
		t.Fatal("no watch")
	}
	v, _, changed := s.snapshot()
	if !v.Ready {
		t.Fatal("not ready")
	}
	close(fail)
	select {
	case <-changed:
	case <-time.After(time.Second):
		t.Fatal("disconnect did not invalidate")
	}
	v, _, _ = s.snapshot()
	if v.Known || v.Ready {
		t.Fatal("stale green after disconnect")
	}
	cancel()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("watch shutdown blocked")
	}
}
