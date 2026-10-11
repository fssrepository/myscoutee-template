ARG NGINX_BASE_IMAGE=myscoutee-nginx:1.3.0-base
FROM golang:1.26-bookworm AS runtime-status-builder
WORKDIR /src
COPY images/nginx/runtime-status/ ./
RUN --network=none GOMAXPROCS=2 CGO_ENABLED=0 GOTOOLCHAIN=local GOPROXY=off go test -p=1 ./... && \
    GOMAXPROCS=2 CGO_ENABLED=0 GOTOOLCHAIN=local GOPROXY=off go build -p=1 -trimpath -ldflags='-s -w' -o /runtime-status-receiver .

FROM ${NGINX_BASE_IMAGE}
COPY --from=runtime-status-builder /runtime-status-receiver /scripts/runtime-status-receiver

ARG TZ=Europe/Brussels
ENV TZ=${TZ}

RUN apk add --no-cache dcron nano curl inotify-tools openssl tzdata jq &&\
    ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime &&\
    echo "${TZ}" > /etc/timezone

COPY images/nginx/dev/nginx-entrypoint.sh /scripts/nginx-entrypoint.sh
COPY --chmod=0755 images/nginx/runtime-status.sh /scripts/runtime-status.sh
RUN chmod +x /scripts/nginx-entrypoint.sh

CMD ["bash", "-c", "/scripts/nginx-entrypoint.sh"]
