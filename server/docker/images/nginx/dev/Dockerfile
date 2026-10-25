ARG NGINX_BASE_IMAGE=ghcr.io/fssrepository/myscoutee-nginx:1.5.0@sha256:19c669eb613b68731e18e5e9b32874af437f82efc7d2c3285c815ca6d6ba00ca
FROM ${NGINX_BASE_IMAGE}
COPY --from=nginx-config http/public-routes.map /etc/nginx/public-routes.map
COPY conf/mongodb/myscoutee_db/countryPartitions.json /etc/nginx/countryPartitions.json

ARG TZ=Europe/Brussels
ENV TZ=${TZ}


CMD ["bash", "-c", "/scripts/nginx-entrypoint.sh"]
