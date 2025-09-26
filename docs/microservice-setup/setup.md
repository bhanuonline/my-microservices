spring-boot:run -Dspring-boot.run.profiles=direct
spring-boot:run -Dspring-boot.run.profiles=eureka

Set Active Profile to direct or eureka. on intelij
mvn dependency:resolve-plugins
mvn clean install -Dmaven.test.skip=true
mvn wrapper:wrapper
./mvnw spring-boot:run
mvn dependency:tree | grep web

mvn dependency:list
jar tf target/eureka-server-1.0.0-SNAPSHOT.jar | grep spring-web
jar tf target/eureka-server.jar | grep AutoConfiguration

