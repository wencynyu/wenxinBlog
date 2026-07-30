# syntax=docker/dockerfile:1
# content-service (Spring Boot 4 / WebFlux / MinIO / RabbitMQ)
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre-alpine
ARG OTEL_AGENT_VERSION=2.30.0
ADD https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/${OTEL_AGENT_VERSION}/opentelemetry-javaagent-${OTEL_AGENT_VERSION}.jar /otel/opentelemetry-javaagent.jar
WORKDIR /app
COPY --from=builder /build/target/*.jar /app/app.jar
EXPOSE 8004
ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
