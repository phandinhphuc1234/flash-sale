# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY libs libs
COPY contracts contracts
COPY services services

RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -pl services/notification-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring --home-dir /app spring \
    && chown -R spring:spring /app

COPY --chown=spring:spring --from=build /workspace/services/notification-service/target/*.jar /app/app.jar

USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
