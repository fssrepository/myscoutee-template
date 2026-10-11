# syntax=docker/dockerfile:1.7

ARG MONGO_BASE_IMAGE=mongo:8.0.4
FROM ${MONGO_BASE_IMAGE}

COPY --from=mongo-seed 00_replica_set.js db_init.js mongo_import.sh mongo-seed-policy.inc replica-entrypoint replica-health /docker-entrypoint-initdb.d/
COPY --from=mongo-seed e2e_db/ /docker-entrypoint-initdb.d/e2e_db/

RUN chmod 0755 /docker-entrypoint-initdb.d/mongo_import.sh
