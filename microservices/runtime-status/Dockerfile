# syntax=docker/dockerfile:1.7
# Reuse the canonical receiver; add only the Kubernetes state source.
ARG FRONTEND_BASE_IMAGE
FROM golang:1.26-bookworm AS runtime-status-builder
WORKDIR /src
COPY --from=runtime-common . ./
COPY *.go ./
RUN --network=none GOMAXPROCS=2 CGO_ENABLED=0 GOTOOLCHAIN=local GOPROXY=off go test -p=1 ./... && \
    GOMAXPROCS=2 CGO_ENABLED=0 GOTOOLCHAIN=local GOPROXY=off go build -p=1 -trimpath -ldflags='-s -w' -o /runtime-status-receiver .
FROM ${FRONTEND_BASE_IMAGE}
COPY --from=runtime-status-builder /runtime-status-receiver /scripts/runtime-status-receiver
