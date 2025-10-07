spring-boot:run -Dspring-boot.run.profiles=direct
spring-boot:run -Dspring-boot.run.profiles=eureka

Set Active Profile to direct or eureka. on intelij
mvn dependency:resolve-plugins
mvn clean install -Dmaven.test.skip=true
mvn wrapper:wrapper
./mvnw spring-boot:run
./mvnw spring-boot:run -Pdebug
./mvnw spring-boot:run -Dspring-boot.run.profiles=preprod,dev

./mvnw spring-boot:run \
-Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006"
mvn dependency:tree | grep web

mvn dependency:list
jar tf target/eureka-server-1.0.0-SNAPSHOT.jar | grep spring-web
jar tf target/eureka-server.jar | grep AutoConfiguration

lsof -i :5005 

#kafka
docker run -d \
--name my-zookeeper \
-e ALLOW_ANONYMOUS_LOGIN=yes \
-p 2181:2181 \
bitnami/zookeeper:latest

docker run -d \
--name my-kafka \
-e ALLOW_PLAINTEXT_LISTENER=yes \
-e KAFKA_CFG_NODE_ID=1 \
-e KAFKA_CFG_PROCESS_ROLES=broker,controller \
-e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
-e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
-e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
-e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@my-kafka:9093 \
-p 9092:9092 \
bitnami/kafka:latest

docker compose logs -f kafka
docker ps
run docker : docker compose -f kafka-compose.yml up -d
check log :docker compose -f kafka-compose.yml logs -f kafka

