# syntax=docker/dockerfile:1
# search-service (Spring Boot 4 / OpenSearch / Kafka)
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar /app/app.jar
EXPOSE 8005
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
