# Stage 1: Build
FROM gradle:9.2-jdk25 AS build
WORKDIR /app
COPY . .
RUN gradle :db-manager:installDist --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/db-manager/build/install/db-manager/ .
ENTRYPOINT ["bin/db-manager"]
