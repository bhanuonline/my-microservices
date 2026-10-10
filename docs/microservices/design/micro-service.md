**What Are Microservices?**
Microservices is an architectural style that structures an application as a collection of small, independent, and loosely coupled services
Microservices are generally independent services that communicate and coordinate with each other to serve the overall functionality of an application

**Communication Patterns**
How Microservices Communicate

    Synchronous Communication(Request-Response)
    Asynchronous Communication (Event-Driven)

    Synchronous Communication
        Common protocols: HTTP/HTTPS (REST), gRPC, GraphQL.
        Flow: One service sends a request to another and waits for a response.
        Ex:
            Service A calls an endpoint exposed by Service B.
            Service B processes the request and sends back a response.
        Pros:   
            Simple and direct.
            Immediate response.
        Cons:
            Tight coupling — if Service B is slow or down, Service A waits or fails.
            Latency adds up when multiple calls are chained
            Harder to scale in long call chains
            Retries and fallbacks must be handled carefully (use Resilience4j)

        Common Tools in Java / Spring:
            RestTemplate (simple HTTP calls)
            WebClient (reactive HTTP client)
            Feign Client (declarative REST client)
            Spring Cloud LoadBalancer (for service-to-service load balancing)

        Ex:
            What actually happens when Service B is down?

            Service A:
                waits until timeout, or
                gets an error (5xx / connection refused)
            This creates:
                ❌ cascading failure
                ❌ slow system
                ❌ bad user experience

        So how do systems avoid this?
        They use 2 approaches:
            1️⃣ Protection patterns (still synchronous)   2️⃣ Asynchronous communication

        1️⃣ Protection patterns (quick intro)
        Even with REST calls, we can protect Service A using:
            Timeout
            Retry
            Circuit Breaker
            Fallback


        Asynchronous Communication :Services send messages or events and do not wait for an immediate response.

            Common protocols/tools: Messaging queues (RabbitMQ, Kafka, Amazon SQS), publish–subscribe models.
            Flow: One service sends a message/event to a queue or topic; other services listen and react.
        Example:
            Payment service publishes an “OrderPaid” event.
            Inventory service consumes that event and updates stock.
        Pros:   
            Loose coupling — services operate independently.
            Better resilience for high-volume systems.
        Cons:
            More complex to design and debug.

        Tools:
            RabbitMQ – message queue (AMQP)
            Apache Kafka – event streaming platform (for high throughput)
            ActiveMQ, AWS SQS, Google Pub/Sub, etc.

        ✅ Pros
            Loosely coupled and highly scalable
            Better fault tolerance — if one service is slow, others keep working
            Great for event-driven architecture
            Enables real-time updates
        ⚠️ Cons
            More complex to design and debug
            Not ideal for real-time responses (since it’s fire-and-forget)
            Requires additional infrastructure (message broker)
            Harder to maintain data consistency

**Service Discovery**

    In a dynamic environment (like Kubernetes or cloud deployments), services often change their network locations (IP, port).
    Service discovery ensures that microservices can find each other without hardcoding addresses.

    Tools: Consul, Eureka, Kubernetes Service (internal DNS names).
    Flow: Service registers itself when it starts → other services query the registry to find it.

**API Gateway**

    An API Gateway acts as a single entry point to route client requests to the right microservice.
    Can handle authentication, rate limiting, transformations.
    Examples: Kong, NGINX, AWS API Gateway.

**What Cloudflare Does**

    Cloudflare acts as:
        CDN (Content Delivery Network) — caches static content near users.
        DNS management — resolves domain names.
        WAF (Web Application Firewall) — protects against attacks.
        DDoS protection — prevents your site from being overwhelmed.
        SSL/TLS management — handles HTTPS certificates.




                                    ┌─────────────────────┐
                                    │    Client Layer      │
                                    │  (Web, Mobile, API)  │
                                    └─────────┬───────────┘
                                              │
                                    ┌─────────────────────┐
                                    │  Edge Layer (CDN/WAF│
                                    │   e.g., Cloudflare) │
                                    └─────────┬───────────┘
                                              │
                                    ┌─────────────────────┐
                                    │    API Gateway       │
                                    │ (Routing, Auth, etc.)│
                                    └─────────┬───────────┘
                                              │
                                    ┌─────────────────────┐
                                    │ Service Discovery    │
                                    │  (Registry/DNS)      │
                                    └──────┬───┬──────────┘
                                           │   │
                            ┌─────────────┘   └───────────────┐
                            │                                  │
                            ┌───────────────┐           ┌────────────────────┐
                            │ Microservice A │  <-----> │ Microservice B      │   (Sync: REST/gRPC)
                            └───────┬────────┘           └─────────┬─────────┘
                            │ (Async Event Bus)             │
                            ▼                                ▼
                            ┌──────────────┐                  ┌──────────────┐
                            │Message Broker │                  │Microservice C│
                            │(Kafka/RabbitMQ│                  └───────┬──────┘
                            └───────┬──────┘                          │
                            │                                  ▼
                            ┌─────────────────────┐       ┌──────────────┐
                            │   Databases (One per │       │ Log/Monitoring│
                            │       Service)       │       │   Layer       │
                            └─────────────────────┘       └──────────────┘


**Monitoring & Logging**

    Track health, logs, and metrics (using Actuator, Prometheus, Grafana, ELK Stack).

**Configuration Server**

    Centralized configuration management for all services (e.g., Spring Cloud Config Server).




