ARG MINIO_BASE_IMAGE=minio/minio:RELEASE.2025-09-07T16-13-09Z

FROM ${MINIO_BASE_IMAGE}

COPY docker/conf/minio /seed
COPY docker/scripts/bootstrap_minio_seed.sh /scripts/bootstrap_minio_seed.sh
COPY docker/scripts/start_minio_with_seed.sh /scripts/start_minio_with_seed.sh

ENTRYPOINT ["/bin/sh", "/scripts/start_minio_with_seed.sh"]
CMD ["server", "/data", "--console-address", ":9001"]
