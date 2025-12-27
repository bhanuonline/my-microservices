
An event-based system (also called event-driven architecture – EDA) is a software design pattern 
where different components communicate by producing and responding to events, instead of calling each other directly.

**Why Use Event-Based Architecture?**
✔️ Loose Coupling :Services don’t call each other directly. They just send/receive events.
✔️ Scalability :Each service can scale independently.
✔️ Asynchronous Processing  : Events can be processed later; no need to wait.
✔️ Reliability : If one service is down, events remain in the broker (Kafka) until the service comes back.
✔️ Real-time Data Flow

**Rule to Remember**

| If you need response immediately                  | → **Use Direct API (non-event-based)** |
| ------------------------------------------------- | -------------------------------------- |
| If work can happen later                          | → **Use Event-Based (Kafka)**          |
| If multiple systems need same data                | → **Use Event-Based**                  |
| If only one system needs response                 | → **Use Direct API**                   |
| If system must not fail even if services are down | → **Use Event-Based**                  |


**what is Kafka ?**
Kafka is one of the most popular platforms for event-based architecture.