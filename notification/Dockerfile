# ---- Build stage ----
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl notification -am clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/notification/target/*.jar app.jar
# notification has no HTTP port by default
ENTRYPOINT ["java", "-jar", "app.jar"]
