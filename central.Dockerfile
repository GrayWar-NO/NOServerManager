# syntax=docker/dockerfile:1.7

FROM gradle:9.2-jdk25 AS build

WORKDIR /app

COPY gradle gradle
COPY gradlew settings.gradle* build.gradle* ./
COPY db-manager/build.gradle* db-manager/

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :proto:build --no-daemon || true

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :db-manager:dependencies --no-daemon || true

COPY . .

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :db-manager:installDist --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/db-manager/build/install/db-manager/ .
ENTRYPOINT ["bin/db-manager"]
