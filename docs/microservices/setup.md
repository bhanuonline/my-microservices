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

clean and re-download dependencies
mvn dependency:purge-local-repository
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

#ProductService
setjava17
port : 8082
jps -v : running java process
⌥ + ⌘ + L : format
mvn clean install -Dmaven.test.skip=true
./mvnw spring-boot:run -Pdebug



#service

    docker run -it --rm maven:3.9.6-eclipse-temurin-17 bash
    docker image
    
    COPY . .
    RUN mvn -pl service -am clean package -DskipTests
    -pl service = build the service module only
    -am = also build its required modules (including the parent)
    docker rmi <image-id>
    docker images
    docker run -p 8080:8080 my-service
    docker logs -f sweet_jemison(service name docker ps check the container)
    
    how to go inside the container
    docker exec -it sweet_jemison /bin/bash
    docker exec sweet_jemison ls -l /app/target
    
    #if db not install on docker then run it and chnage the db name 
    docker run -d \
    --name my-mysql \
    -e MYSQL_ROOT_PASSWORD=root \
    -e MYSQL_DATABASE=servicedb \
    -p 3307:3306 \
    mysql:8
    sudo lsof -i :3306
    
    # list running containers
    docker ps
    # stop/remove existing MySQL container if appropriate
    docker stop <container_id>
    docker rm <container_id>
    
    mvn -pl service -am clean package -DskipTests # on root folder run
    docker build -t my-service:latest .
    
    #if sumthing change then
    mvn clean package
    docker build -t my-service .
    docker run -p 8093:8093 my-service
    
    #run time
    docker run -d \
    -p 8080:8080 \
    -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3307/mydb \
    -e SPRING_DATASOURCE_USERNAME=root \
    -e SPRING_DATASOURCE_PASSWORD=secret \
    my-service
    
    docker image history my-service
    
    CREATE USER 'appuser'@'%' IDENTIFIED BY 'appsecret';
    GRANT ALL PRIVILEGES ON mydb.* TO 'appuser'@'%';
    FLUSH PRIVILEGES;
    
    or
    ALTER USER 'root'@'%' IDENTIFIED BY 'secret';
    FLUSH PRIVILEGES;

