# syntax=docker/dockerfile:1.7

ARG NODE_BASE_IMAGE=node:22-alpine
ARG NGINX_BASE_IMAGE=ghcr.io/fssrepository/myscoutee-nginx:1.5.0@sha256:9ab49ea949afaee5164e828b699c102dca38a2766d7573c4f342984e8da74774

FROM ${NODE_BASE_IMAGE} AS frontend-builder

ENV CI=true \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
WORKDIR /frontend

COPY --from=frontend-source package.json package-lock.json .npmrc ./
RUN --mount=type=secret,id=npmrc,target=/root/.npmrc,required=true \
    --mount=type=cache,id=myscoutee-e2e-npm,target=/root/.npm \
    npm ci --no-audit --no-fund

COPY --from=frontend-source angular.json tsconfig*.json ./
COPY --from=frontend-source src/ ./src/
COPY --from=frontend-source scripts/ ./scripts/
RUN npm run build:e2e

FROM ${NGINX_BASE_IMAGE}
COPY --from=nginx-config http/public-routes.map /etc/nginx/public-routes.map
COPY --from=nginx-partitions countryPartitions.json /etc/nginx/countryPartitions.json

COPY --from=nginx-config http/runtime-status.conf /etc/nginx/conf.d/runtime-status.conf
COPY --from=nginx-config nginx.conf /etc/nginx/nginx.conf
COPY --from=nginx-config http/spring.conf /etc/nginx/conf.d/spring.conf
RUN sed -i 's/server_name localhost;/server_name 127.0.0.1;/g' /etc/nginx/conf.d/spring.conf
COPY --from=nginx-config http/routing.conf /etc/nginx/conf.d/routing.conf
COPY --from=nginx-config http/http-transport-policy.prod.conf /etc/nginx/conf.d/http-transport-policy.conf
COPY --from=nginx-config http/https-response-policy.prod.conf /etc/nginx/conf.d/https-response-policy.conf
COPY --from=nginx-config stream/mongodb.conf /etc/nginx/stream_conf.d/mongodb.conf
COPY --from=frontend-builder /frontend/e2e/ /usr/share/nginx/html/

EXPOSE 80 443 27017

ENTRYPOINT ["/scripts/nginx-e2e-entrypoint.sh"]
