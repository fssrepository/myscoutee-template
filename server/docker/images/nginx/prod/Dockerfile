ARG NGINX_BASE_IMAGE=myscoutee-nginx:1.4.0-base
FROM golang:1.26-bookworm AS runtime-status-builder
WORKDIR /src
COPY images/nginx/runtime-status/ ./
RUN --network=none GOMAXPROCS=2 CGO_ENABLED=0 GOTOOLCHAIN=local GOPROXY=off go test -p=1 ./... && \
    GOMAXPROCS=2 CGO_ENABLED=0 GOTOOLCHAIN=local GOPROXY=off go build -p=1 -trimpath -ldflags='-s -w' -o /runtime-status-receiver .

FROM ${NGINX_BASE_IMAGE}
COPY --from=runtime-status-builder /runtime-status-receiver /scripts/runtime-status-receiver


COPY conf/nginx/nginx.conf /etc/nginx/nginx.conf
COPY conf/nginx/conf/http/spring.conf /etc/nginx/myscoutee-templates/spring.conf
COPY conf/nginx/conf/http/routing.conf /etc/nginx/conf.d/routing.conf
COPY conf/nginx/conf/http/runtime-status.conf /etc/nginx/conf.d/runtime-status.conf
COPY --chmod=0755 images/nginx/runtime-status.sh /scripts/runtime-status.sh
COPY conf/nginx/conf/http/update-status.prod.conf /etc/nginx/conf.d/update-status.prod.conf
RUN printf '\ninclude /etc/nginx/conf.d/update-status.prod.conf;\n' >> /etc/nginx/conf.d/routing.conf
COPY conf/nginx/conf/http/http-transport-policy.prod.conf /etc/nginx/conf.d/http-transport-policy.conf
COPY conf/nginx/conf/http/https-response-policy.prod.conf /etc/nginx/conf.d/https-response-policy.conf
COPY conf/nginx/conf/stream/mongodb.conf /etc/nginx/stream_conf.d/mongodb.conf
COPY --chmod=0755 images/nginx/prod/nginx-entrypoint.sh /scripts/nginx-production-entrypoint.sh
COPY --from=frontend-prod . /usr/share/nginx/html

CMD ["/scripts/nginx-production-entrypoint.sh"]
