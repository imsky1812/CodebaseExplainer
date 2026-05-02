# ── Build stage ────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ── Run stage ──────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY src/main/resources/prompts.json ./prompts.json
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=${PORT:8000}"]
