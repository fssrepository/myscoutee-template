ARG NGINX_BASE_IMAGE=ghcr.io/fssrepository/myscoutee-nginx:1.5.0@sha256:9ab49ea949afaee5164e828b699c102dca38a2766d7573c4f342984e8da74774
FROM ${NGINX_BASE_IMAGE}
COPY --from=nginx-config http/public-routes.map /etc/nginx/public-routes.map
COPY conf/mongodb/myscoutee_db/countryPartitions.json /etc/nginx/countryPartitions.json

ARG TZ=Europe/Brussels
ENV TZ=${TZ}


CMD ["bash", "-c", "/scripts/nginx-entrypoint.sh"]
