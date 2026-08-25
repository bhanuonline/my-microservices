Error: java.lang.RuntimeException: No readable meta.properties files found.
Solution :
bin/kafka-storage.sh format \
--config config/kraft/server.properties \
--cluster-id $(bin/kafka-storage.sh random-uuid)
