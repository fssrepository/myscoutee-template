FROM nginx:1.29.6-alpine

RUN apk del curl &&\
    apk add --no-cache bash netcat-openbsd

COPY images/nginx/wait-for-it.sh /scripts/wait-for-it.sh
RUN chmod +x /scripts/wait-for-it.sh

CMD ["nginx", "-g", "daemon off;"]
