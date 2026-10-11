.PHONY: test build image
GO ?= go
VERSION ?= 1.0.0
IMAGE ?= myscoutee-template-mcp:$(VERSION)
test:
	$(GO) test -race ./...
build:
	CGO_ENABLED=0 $(GO) build -trimpath -ldflags='-s -w' -o build/myscoutee-mcp ./cmd/myscoutee-mcp
image:
	docker build -t $(IMAGE) .
