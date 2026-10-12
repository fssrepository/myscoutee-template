FROM eclipse-temurin:25.0.2_10-jdk

RUN apt-get update && \
    apt-get install -y --no-install-recommends imagemagick && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
