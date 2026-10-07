#For Development (default)
mvn spring-boot:run -Pdev

override property directly:
mvn spring-boot:run -Dspring-boot.run.profiles=dev

#FOR PROD
mvn clean package -Pprod
java -jar target/service-1.0.0.jar --spring.profiles.active=prod