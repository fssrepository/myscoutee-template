# syntax=docker/dockerfile:1.7

ARG E2E_BUILD_IMAGE=eclipse-temurin:25.0.2_10-jdk
ARG E2E_RUNTIME_IMAGE=eclipse-temurin:25.0.2_10-jre-alpine

FROM ${E2E_BUILD_IMAGE} AS builder

ARG MYSCOUTEE_VERSION=1.0.0
ENV MYSCOUTEE_VERSION=${MYSCOUTEE_VERSION}

WORKDIR /workspace

COPY gradlew settings.gradle ./
COPY gradle/ ./gradle/
COPY projects/profile/build.gradle ./projects/profile/build.gradle
COPY projects/profile/src/main/ ./projects/profile/src/main/

RUN --mount=type=cache,id=myscoutee-template-e2e-gradle,target=/root/.gradle \
    ./gradlew \
      --no-daemon \
      --project-cache-dir /tmp/myscoutee-project-cache \
      :projects:profile:bootJar && \
    test "$(find projects/profile/build/libs -maxdepth 1 -type f -name 'profile-*.jar' | wc -l)" -eq 1

FROM ${E2E_RUNTIME_IMAGE}

ARG TZ=Europe/Brussels
ENV HOME=/tmp \
    TZ=${TZ}

EXPOSE 8080

RUN apk add --no-cache imagemagick libwebp-tools tzdata && \
    ln -snf "/usr/share/zoneinfo/${TZ}" /etc/localtime && \
    echo "${TZ}" > /etc/timezone && \
    addgroup -S -g 10001 myscoutee && \
    adduser -S -D -H -u 10001 -G myscoutee myscoutee && \
    install -d -o myscoutee -g myscoutee \
      /app \
      /etc/myscoutee/configuration

COPY --from=builder --chown=10001:10001 \
    /workspace/projects/profile/build/libs/profile-*.jar \
    /app/spring-boot-application.jar
COPY --chmod=0555 \
    docker/scripts/ensure_operator_configuration_master_key.sh \
    /usr/local/bin/ensure-operator-configuration-key
COPY --chmod=0555 \
    docker/images/profilemanagement/e2e/entrypoint.sh \
    /usr/local/bin/myscoutee-e2e-entrypoint

WORKDIR /app
USER 10001:10001

ENTRYPOINT ["/usr/local/bin/myscoutee-e2e-entrypoint"]
