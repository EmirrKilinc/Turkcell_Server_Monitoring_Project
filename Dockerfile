# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /data01
COPY --from=build /app/target/monitoring-poc.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
