# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services services
ARG SERVICE
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl services/${SERVICE} -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG SERVICE
COPY --from=build /workspace/services/${SERVICE}/target/*.jar app.jar
RUN addgroup -S relayforge && adduser -S relayforge -G relayforge && chown relayforge:relayforge /app
USER relayforge
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
