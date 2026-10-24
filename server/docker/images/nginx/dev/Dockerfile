ARG NGINX_BASE_IMAGE=ghcr.io/fssrepository/myscoutee-nginx:1.5.0@sha256:7ec3ba085e30314d5536401ac22e14064d30a5461bfc1a9ccbe0e6340b6c6c24
FROM ${NGINX_BASE_IMAGE}
COPY --from=nginx-config http/public-routes.map /etc/nginx/public-routes.map
COPY conf/mongodb/myscoutee_db/countryPartitions.json /etc/nginx/countryPartitions.json

ARG TZ=Europe/Brussels
ENV TZ=${TZ}


CMD ["bash", "-c", "/scripts/nginx-entrypoint.sh"]
