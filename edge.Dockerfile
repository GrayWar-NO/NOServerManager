# Stage 1: Build
FROM gradle:9.2-jdk25 AS build
WORKDIR /app
COPY . .
RUN gradle :edge-agent:installDist --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/edge-agent/build/install/edge-agent/ .
RUN apt-get update && apt-get install -y curl \
    && curl -L -o /tmp/dockerize-linux-amd64.tar.gz \
       https://github.com/jwilder/dockerize/releases/download/v0.8.0/dockerize-linux-amd64-v0.8.0.tar.gz \
    && tar -C /usr/local/bin -xzf /tmp/dockerize-linux-amd64.tar.gz \
    && rm /tmp/dockerize-linux-amd64.tar.gz \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*   
ENTRYPOINT ["dockerize", "-wait", "tcp://no.server.internal:10042", "-timeout", "60s", "bin/edge-agent"]
