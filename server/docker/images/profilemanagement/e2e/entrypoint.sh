#!/bin/sh
set -eu

umask 077
/usr/local/bin/ensure-operator-configuration-key

exec java \
  -XX:+UnlockExperimentalVMOptions \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /app/spring-boot-application.jar \
  "$@"

