# ---- Stage 1: Build ----
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY .  .
#COPY src ./src
#COPY . .
#RUN mvn clean package -Dmaven.test.skip=true
# Build only the desired module and its dependencies
#RUN mvn -pl service -am clean package -DskipTests
# Build only the desired module and its dependencies
RUN mvn -pl service -am clean package -DskipTests

# ---- Stage 2: Run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
# Copy only the built jar from previous stage
COPY --from=build /app/service/target/service-1.0.0-SNAPSHOT.jar app.jar

# Expose Spring Boot default port
EXPOSE 8080

# Optionally set spring profiles if needed (e.g., dev)
ENV SPRING_PROFILES_ACTIVE=default

ENTRYPOINT ["java", "-jar", "app.jar"]