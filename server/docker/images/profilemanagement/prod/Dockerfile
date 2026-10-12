FROM eclipse-temurin:25.0.2_10-jre-alpine AS jre-runtime

EXPOSE 8080

RUN addgroup -S -g 10001 myscoutee && \
    adduser -S -D -H -u 10001 -G myscoutee myscoutee && \
    install -d -o myscoutee -g myscoutee /app

FROM jre-runtime AS media-runtime
RUN apk add --no-cache imagemagick libwebp-tools

# Preserve the monolith production runtime while exporting the two small base stages.
FROM media-runtime AS application
RUN install -d -o myscoutee -g myscoutee \
      /var/lib/myscoutee/update/staging \
      /var/lib/myscoutee/update/requests \
      /var/lib/myscoutee/update/jobs

ARG MYSCOUTEE_VERSION=1.4.0
COPY --chown=myscoutee:myscoutee profile-${MYSCOUTEE_VERSION}.jar /app/spring-boot-application.jar

USER 10001:10001

ENTRYPOINT ["java", "-XX:+UnlockExperimentalVMOptions", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/spring-boot-application.jar"]
