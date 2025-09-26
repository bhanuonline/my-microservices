# My Microservices Project

# 1. Build
mvn clean install

# 2. Run User Service
cd user-service && mvn spring-boot:run

# (In another terminal)
cd product-service && mvn spring-boot:run

./mvnw spring-boot:run -Dspring-boot.run.arguments=--debug -DskipTests
