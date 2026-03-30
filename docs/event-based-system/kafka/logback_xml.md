**Basic Structure of logback.xml**
A typical logback.xml contains:

Appenders – Define where the logs go (console, file, rolling file, Kafka, etc.)
Loggers – Define logging levels for packages/classes.
Root Logger – Default logging level if no specific logger matches.

How This Works
Patterns:
Date + level + thread + logger + message.
Two separate files:
app-info.log → Only INFO-level events
app-error.log → Only ERROR-level events
Async appenders:
Prevent Kafka producer threads from being blocked by slow I/O.
Log rotation policy:
Daily rollover.
Keep logs for 30 days.
Debugging Kafka Producer:
"org.apache.kafka" at DEBUG level.
"org.apache.kafka.clients.producer" at TRACE gives deep protocol-level details.
Console logging stays (helpful during local development).