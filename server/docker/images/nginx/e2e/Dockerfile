# syntax=docker/dockerfile:1.7

ARG NODE_BASE_IMAGE=node:22-alpine
ARG NGINX_BASE_IMAGE=nginx:1.29.6-alpine

FROM ${NODE_BASE_IMAGE} AS frontend-builder

ENV CI=true \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
WORKDIR /frontend

COPY --from=frontend-source package.json package-lock.json ./
COPY --from=frontend-source components/package.json ./components/package.json
RUN --mount=type=cache,id=myscoutee-e2e-npm,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY --from=frontend-source angular.json tsconfig*.json ./
COPY --from=frontend-source src/ ./src/
COPY --from=frontend-source scripts/ ./scripts/
COPY --from=frontend-source components/src/ ./components/src/
COPY --from=frontend-source components/styles/ ./components/styles/
COPY --from=frontend-source components/ng-package.json components/tsconfig.lib.json ./components/
RUN npm run build:e2e

FROM golang:1.26-bookworm AS runtime-status-builder
WORKDIR /src
COPY --from=nginx-runtime . ./
RUN --network=none GOMAXPROCS=2 CGO_ENABLED=0 GOTOOLCHAIN=local GOPROXY=off go test -p=1 ./... && \
    GOMAXPROCS=2 CGO_ENABLED=0 GOTOOLCHAIN=local GOPROXY=off go build -p=1 -trimpath -ldflags='-s -w' -o /runtime-status-receiver .

FROM ${NGINX_BASE_IMAGE}
COPY --from=runtime-status-builder /runtime-status-receiver /scripts/runtime-status-receiver

RUN apk add --no-cache openssl

COPY --from=nginx-config conf/http/runtime-status.conf /etc/nginx/conf.d/runtime-status.conf
COPY --from=nginx-config nginx.conf /etc/nginx/nginx.conf
COPY --from=nginx-config conf/http/spring.conf /etc/nginx/conf.d/spring.conf
RUN sed -i 's/server_name localhost;/server_name 127.0.0.1;/g' /etc/nginx/conf.d/spring.conf
COPY --from=nginx-config conf/http/routing.conf /etc/nginx/conf.d/routing.conf
COPY --from=nginx-config conf/http/http-transport-policy.prod.conf /etc/nginx/conf.d/http-transport-policy.conf
COPY --from=nginx-config conf/http/https-response-policy.prod.conf /etc/nginx/conf.d/https-response-policy.conf
COPY --from=nginx-config conf/stream/mongodb.conf /etc/nginx/stream_conf.d/mongodb.conf
COPY --chmod=0555 entrypoint.sh /usr/local/bin/myscoutee-nginx-e2e-entrypoint
COPY --from=frontend-builder /frontend/e2e/ /usr/share/nginx/html/

EXPOSE 80 443 27017

ENTRYPOINT ["/usr/local/bin/myscoutee-nginx-e2e-entrypoint"]
