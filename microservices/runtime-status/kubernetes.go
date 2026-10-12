package main

// This file is compiled alongside the canonical status receiver only by the
// microservices image. No Kubernetes implementation enters the monolith image.
import (
	"bufio"
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"time"
)

type sliceMeta struct {
	Name    string            `json:"name"`
	Version string            `json:"resourceVersion"`
	Labels  map[string]string `json:"labels"`
}
type endpointSlice struct {
	Metadata  sliceMeta `json:"metadata"`
	Endpoints []struct {
		Conditions struct {
			Ready       *bool `json:"ready"`
			Terminating bool  `json:"terminating"`
		} `json:"conditions"`
	} `json:"endpoints"`
}
type kubeSource struct {
	mu        sync.Mutex
	expected  map[string]bool
	slices    map[string]endpointSlice
	known     bool
	changed   chan struct{}
	client    *http.Client
	endpoint  string
	tokenPath string
	version   string
}

func init() { infrastructureSource = newKubernetesSource }
func newKubernetesSource(ctx context.Context) (statusSource, error) {
	namespace := os.Getenv("POD_NAMESPACE")
	if !identifier.MatchString(namespace) {
		return nil, errors.New("invalid Kubernetes namespace")
	}
	expected := map[string]bool{}
	for _, name := range strings.Split(os.Getenv("MYSCOUTEE_RUNTIME_SERVICES"), ",") {
		if !identifier.MatchString(name) {
			return nil, errors.New("invalid expected service")
		}
		expected[name] = true
	}
	root := "/var/run/secrets/kubernetes.io/serviceaccount/"
	ca, err := os.ReadFile(root + "ca.crt")
	if err != nil {
		return nil, err
	}
	pool := x509.NewCertPool()
	if !pool.AppendCertsFromPEM(ca) {
		return nil, errors.New("invalid Kubernetes CA")
	}
	transport := &http.Transport{TLSClientConfig: &tls.Config{RootCAs: pool, MinVersion: tls.VersionTLS12}, ResponseHeaderTimeout: 5 * time.Second, TLSHandshakeTimeout: 5 * time.Second, IdleConnTimeout: 60 * time.Second}
	q := url.Values{"labelSelector": {"kubernetes.io/service-name in (" + os.Getenv("MYSCOUTEE_RUNTIME_SERVICES") + ")"}}
	s := &kubeSource{expected: expected, slices: map[string]endpointSlice{}, changed: make(chan struct{}), tokenPath: root + "token", client: &http.Client{Transport: transport, Timeout: 40 * time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}, endpoint: "https://kubernetes.default.svc/apis/discovery.k8s.io/v1/namespaces/" + namespace + "/endpointslices?" + q.Encode()}
	go s.run(ctx)
	return s, nil
}
func (s *kubeSource) readyLocked() bool {
	if !s.known {
		return false
	}
	up := map[string]bool{}
	for _, slice := range s.slices {
		service := slice.Metadata.Labels["kubernetes.io/service-name"]
		for _, endpoint := range slice.Endpoints {
			if endpoint.Conditions.Ready != nil && *endpoint.Conditions.Ready && !endpoint.Conditions.Terminating {
				up[service] = true
				break
			}
		}
	}
	for service := range s.expected {
		if !up[service] {
			return false
		}
	}
	return len(s.expected) > 0
}
func (s *kubeSource) snapshot() (statusValue, time.Time, <-chan struct{}) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return statusValue{s.readyLocked(), s.known, time.Now().Unix()}, time.Time{}, s.changed
}
func (s *kubeSource) update(fn func()) {
	s.mu.Lock()
	defer s.mu.Unlock()
	before, known := s.readyLocked(), s.known
	fn()
	if before != s.readyLocked() || known != s.known {
		close(s.changed)
		s.changed = make(chan struct{})
	}
}
func (s *kubeSource) request(ctx context.Context, suffix string) (*http.Response, error) {
	// Projected service-account tokens rotate; read the current token per connection.
	token, err := os.ReadFile(s.tokenPath)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, "GET", s.endpoint+suffix, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+strings.TrimSpace(string(token)))
	req.Header.Set("Accept", "application/json")
	return s.client.Do(req)
}
func (s *kubeSource) list(ctx context.Context) error {
	response, err := s.request(ctx, "")
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != 200 {
		return fmt.Errorf("Kubernetes list HTTP %d", response.StatusCode)
	}
	b, err := io.ReadAll(io.LimitReader(response.Body, 4*1024*1024+1))
	if err != nil {
		return err
	}
	if len(b) > 4*1024*1024 {
		return errors.New("EndpointSlice list too large")
	}
	var list struct {
		Metadata struct {
			Version  string `json:"resourceVersion"`
			Continue string `json:"continue"`
		} `json:"metadata"`
		Items []endpointSlice `json:"items"`
	}
	if err = json.Unmarshal(b, &list); err != nil {
		return err
	}
	if list.Metadata.Version == "" || list.Metadata.Continue != "" || len(list.Items) > 2048 {
		return errors.New("incomplete EndpointSlice list")
	}
	slices := map[string]endpointSlice{}
	for _, v := range list.Items {
		if s.expected[v.Metadata.Labels["kubernetes.io/service-name"]] {
			slices[v.Metadata.Name] = v
		}
	}
	s.version = list.Metadata.Version
	s.update(func() { s.slices = slices; s.known = true })
	return nil
}

var expiredVersion = errors.New("expired resource version")

func (s *kubeSource) watch(ctx context.Context) error {
	response, err := s.request(ctx, "&watch=true&allowWatchBookmarks=true&timeoutSeconds=30&resourceVersion="+url.QueryEscape(s.version))
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode == 410 {
		return expiredVersion
	}
	if response.StatusCode != 200 {
		return fmt.Errorf("Kubernetes watch HTTP %d", response.StatusCode)
	}
	started := time.Now()
	scanner := bufio.NewScanner(response.Body)
	scanner.Buffer(make([]byte, 4096), 1024*1024)
	for scanner.Scan() {
		var event struct {
			Type   string          `json:"type"`
			Object json.RawMessage `json:"object"`
		}
		if err = json.Unmarshal(scanner.Bytes(), &event); err != nil {
			return err
		}
		if event.Type == "ERROR" {
			var status struct {
				Code int `json:"code"`
			}
			json.Unmarshal(event.Object, &status)
			if status.Code == 410 {
				return expiredVersion
			}
			return errors.New("Kubernetes watch error")
		}
		var item endpointSlice
		if err = json.Unmarshal(event.Object, &item); err != nil {
			return err
		}
		if item.Metadata.Version == "" {
			return errors.New("missing resource version")
		}
		switch event.Type {
		case "BOOKMARK":
		case "ADDED", "MODIFIED", "DELETED":
			if s.expected[item.Metadata.Labels["kubernetes.io/service-name"]] {
				if event.Type != "DELETED" && len(s.slices) >= 2048 {
					return errors.New("too many EndpointSlices")
				}
				s.update(func() {
					if event.Type == "DELETED" {
						delete(s.slices, item.Metadata.Name)
					} else {
						s.slices[item.Metadata.Name] = item
					}
				})
			}
		default:
			return errors.New("unexpected Kubernetes watch event")
		}
		s.version = item.Metadata.Version
	}
	if err = scanner.Err(); err != nil {
		return err
	}
	if time.Since(started) < 25*time.Second {
		return io.ErrUnexpectedEOF
	}
	return nil // expected API watch timeout; resume from the last resourceVersion
}
func (s *kubeSource) run(ctx context.Context) {
	for ctx.Err() == nil {
		var err error
		if s.version == "" {
			err = s.list(ctx)
		}
		if err == nil {
			err = s.watch(ctx)
		}
		if err == nil {
			continue
		}
		s.update(func() { s.known = false })
		s.version = "" // fresh authoritative snapshot after any interrupted observation
		select {
		case <-ctx.Done():
			return
		case <-time.After(2 * time.Second):
		}
	}
}
