FROM mongo:8.0.4

ARG TZ=Europe/Brussels

RUN apt-get update && \
    apt-get install -y --no-install-recommends bash inotify-tools tzdata && \
    ln -snf "/usr/share/zoneinfo/${TZ}" /etc/localtime && \
    echo "${TZ}" > /etc/timezone && \
    rm -rf /var/lib/apt/lists/*

COPY docker/images/mongodb-sync/dev/mongodb-seed-sync.sh /usr/local/bin/mongodb-seed-sync

RUN chmod 0755 /usr/local/bin/mongodb-seed-sync
