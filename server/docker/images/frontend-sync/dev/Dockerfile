FROM node:22-alpine

ARG TZ=Europe/Brussels
ENV TZ=${TZ}

RUN apk add --no-cache bash rsync inotify-tools tzdata && \
    cp /usr/share/zoneinfo/${TZ} /etc/localtime && \
    echo "${TZ}" > /etc/timezone

CMD ["bash", "-c", "while true; do sleep 3600; done"]
