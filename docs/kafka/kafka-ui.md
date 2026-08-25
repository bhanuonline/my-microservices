**Kafka ui setup command :**
docker run -d \
-p 8080:8080 \
-e KAFKA_CLUSTERS_0_NAME=local \
-e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092 \
provectuslabs/kafka-ui:latest

Now verify it’s pointing to your local Kafka (host.docker.internal:9092).
Step 1: Check environment inside the container
Run:
docker exec -it great_lehmann /bin/sh
Inside the container, check the bootstrap servers:
echo $KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS

Step 2: Test connectivity from inside the container
Inside the same shell:
nc -vz host.docker.internal 9092

If env var was missing, stop and remove the container:
docker stop great_lehmann
docker rm great_lehmann


Then start it again:
docker run -d \
--name kafka-ui \
-p 8080:8080 \
-e KAFKA_CLUSTERS_0_NAME=local \
-e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092 \
provectuslabs/kafka-ui:latest

docker run -d \
--name kafka-ui \
-p 9092:8080 \
-e KAFKA_CLUSTERS_0_NAME=local \
-e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=localhost:9092 \
provectuslabs/kafka-ui:latest

Below command for different listener
docker run -d \
--name kafka-ui \
-p 8080:8080 \
-e KAFKA_CLUSTERS_0_NAME=local \
-e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:29092 \
provectuslabs/kafka-ui:latest

[ Browser ] http://localhost:8090
|
V
HOST (Your machine)
Port 8090  ------------------\
                                \
                                    [ Docker port mapping ]
                                        \
                                        \--> CONTAINER (Kafka UI)
                                        Port 8080
Browser --> Host machine port 8090 --> Docker port mapping --> Container port 8080 --> Kafka UI

Step-by-step explanation:
docker run

This tells Docker to start a new container from a specified image.
-d

Run the container in detached mode (in the background).
--name kafka-ui

Assigns the container a name (kafka-ui) so you can refer to it easily in other docker commands (e.g., docker stop kafka-ui).
-p 8080:8080

Maps port 8080 on your host machine to port 8080 in the container.
This means you can access the Kafka UI in your browser at http://localhost:8080.
-e KAFKA_CLUSTERS_0_NAME=local

Sets an environment variable inside the container.
KAFKA_CLUSTERS_0_NAME defines the display name of the Kafka cluster in the Kafka UI (“local” here).
-e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092

Another environment variable.
KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS tells Kafka UI where to connect to your Kafka brokers.
host.docker.internal is a special DNS name in Docker to refer to the host machine running Docker (useful, because if Kafka is running on your host machine at port 9092, the container needs this to reach it).
provectuslabs/kafka-ui:latest

This is the Docker image used. It is the latest version of Kafka UI published by Provectus Labs.

**docker-compose.yml**
version: '3.8'
services:
kafka-ui:
image: provectuslabs/kafka-ui:latest
container_name: kafka-ui
ports:
- "8080:8080" //-p HOST_PORT:CONTAINER_PORT
environment:
- KAFKA_CLUSTERS_0_NAME=local
- KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=host.docker.internal:9092

Confirm from inside Docker you can reach Kafka:
docker exec -it kafka-ui ping host.docker.internal

**Network View**
┌───────────────────────────┐
│        Kafka Broker        │
│ listeners:                 │
│   PLAINTEXT://0.0.0.0:9092 │ <── Host CLI
│   PLAINTEXT_DOCKER://0.0.0.0:29092 │ <── Docker UI
│ advertised:                │
│   PLAINTEXT://localhost:9092       │
│   PLAINTEXT_DOCKER://host.docker.internal:29092 │
└───────────────────────────┘
↑ localhost:9092                ↑ host.docker.internal:29092
│                                │
┌─────┴─────┐                   ┌─────┴─────┐
│  Host CLI │                   │ Docker UI │
└───────────┘                   └───────────┘
