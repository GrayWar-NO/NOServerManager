# syntax=docker/dockerfile:1.7

FROM gradle:9.2-jdk25 AS build

WORKDIR /app

COPY gradle gradle
COPY gradlew settings.gradle* build.gradle* ./
COPY edge-agent/build.gradle* edge-agent/

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :proto:build --no-daemon || true

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :edge-agent:dependencies --no-daemon || true


COPY . .

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :edge-agent:installDist --no-daemon


FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/edge-agent/build/install/edge-agent/ .
ENTRYPOINT ["bin/edge-agent"]