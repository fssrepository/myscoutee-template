ARG NGINX_BASE_IMAGE=ghcr.io/fssrepository/myscoutee-nginx:1.5.0@sha256:19c669eb613b68731e18e5e9b32874af437f82efc7d2c3285c815ca6d6ba00ca
FROM ${NGINX_BASE_IMAGE}
ARG MYSCOUTEE_VERSION=1.5.0
LABEL io.myscoutee.service="nginx" \
      io.myscoutee.package-version="${MYSCOUTEE_VERSION}"
COPY --from=nginx-config http/public-routes.map /etc/nginx/public-routes.map
COPY conf/mongodb/myscoutee_db/countryPartitions.json /etc/nginx/countryPartitions.json


COPY --from=nginx-config nginx.conf /etc/nginx/nginx.conf
COPY --from=nginx-config http/spring.conf /etc/nginx/myscoutee-templates/spring.conf
COPY --from=nginx-config http/routing.conf /etc/nginx/conf.d/routing.conf
COPY --from=nginx-config http/runtime-status.conf /etc/nginx/conf.d/runtime-status.conf
COPY --from=nginx-config http/update-status.prod.conf /etc/nginx/conf.d/update-status.prod.conf
RUN printf '\ninclude /etc/nginx/conf.d/update-status.prod.conf;\n' >> /etc/nginx/conf.d/routing.conf
COPY --from=nginx-config http/http-transport-policy.prod.conf /etc/nginx/conf.d/http-transport-policy.conf
COPY --from=nginx-config http/https-response-policy.prod.conf /etc/nginx/conf.d/https-response-policy.conf
COPY --from=nginx-config stream/mongodb.conf /etc/nginx/stream_conf.d/mongodb.conf
COPY --from=frontend-prod . /usr/share/nginx/html

CMD ["/scripts/nginx-production-entrypoint.sh"]
