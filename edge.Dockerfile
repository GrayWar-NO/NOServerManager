FROM gradle:9.2-jdk25 AS build
WORKDIR /app
COPY . .
RUN gradle :edge-agent:installDist --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/edge-agent/build/install/edge-agent/ .
ENTRYPOINT ["bin/edge-agent"]