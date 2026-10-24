ARG NGINX_BASE_IMAGE=ghcr.io/fssrepository/myscoutee-nginx:1.5.0@sha256:7ec3ba085e30314d5536401ac22e14064d30a5461bfc1a9ccbe0e6340b6c6c24
FROM ${NGINX_BASE_IMAGE}
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
