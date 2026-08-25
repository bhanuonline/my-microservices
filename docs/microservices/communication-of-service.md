**how two services (like two microservices or servers) communicate with each other**

**HTTP-based communication (Synchronous)**

    Service A makes an HTTP request to Service B’s REST or GraphQL API
    Tools:REST APIs (JSON over HTTP),GraphQL APIs
    Advantages: Simple, widely supported.
    Disadvantages: Tight coupling,latency, reliability issues

    EX:
    Service A  ----HTTP POST /users---->  Service B
               (JSON payload)
           <----HTTP 200 OK (response)

**gRPC (Synchronous but more efficient)**

    Protocol: HTTP/2 + Protocol Buffers
    Advantages: Faster, strongly typed, ideal for internal microservices.
    Disadvantages: More setup complexity.

**Message Queue / Event Bus (Asynchronous)**

    Example: Service A sends a message to a queue without waiting to reply, Service B reads and processes it.
    Tools:RabbitMQ ,Kafka ,AWS SQS ,Google Pub/Sub
    Advantages:Decoupled services. Resilient to temporary service outages. Scalable. 
    Disadvantages:More complex infrastructure. Harder to trace flow end-to-end.

    Service A ----PUT message---->  Queue (Kafka)
                                |
                                v
                          Service B consumes

| Scenario                    | Best Method             |
| --------------------------- | ----------------------- |
| Immediate response required | REST / Feign            |
| Heavy load / event driven   | Kafka / RabbitMQ        |
| Clean readable code         | Feign                   |
| Resiliency required         | Feign + Circuit Breaker |


**Supporting Features**
Service discovery: Dynamically locate other services (used with tools like Consul, Eureka, or Kubernetes DNS).
Load balancing: Distribute traffic evenly across multiple instances.
Circuit breaker: Prevent cascading failures (via libraries like Resilience4J, Istio).
Retries+Timeouts: Handle transient failures gracefully.
Security: Usually token-based (JWT, OAuth2).
Use Feign Client or WebClient (Reactive) instead of RestTemplate for modern, cleaner code.
Add timeouts, retries, and circuit breakers (e.g., with Resilience4J or Spring Cloud CircuitBreaker).
Use discovery (Eureka, Consul) if you have multiple instances.
Secure internal calls using API keys or tokens when needed.
Feign → easiest when using Spring Cloud microservices.
WebClient → for reactive or high-performance REST calls.

**Gateway handles:**

Routing
Authentication
Rate limiting
Logging
Circuit breaker
Load balancing

**Why API Gateway is Mandatory in Production**

| Problem         | Without Gateway            | With Gateway           |
| --------------- | -------------------------- | ---------------------- |
| Security        | Every service handles auth | Centralized at Gateway |
| Logging         | Scattered logs             | One place              |
| URL changes     | Client breaks              | Gateway hides changes  |
| Rate limiting   | Hard to manage             | Easy                   |
| Failure control | Cascade failures           | Circuit breaker        |


| Feature     | API Gateway                            | Eureka                                 |
| ----------- | -------------------------------------- | -------------------------------------- |
| What it is  | **Entry gate for all client requests** | **Service registry / phone directory** |
| Who uses it | Client / UI                            | Microservices                          |
| Purpose     | Routing, security, rate-limit, auth    | Service discovery                      |
| Example     | Spring Cloud Gateway                   | Netflix Eureka Server                  |

| API Gateway                  | Eureka                         |
| ---------------------------- | ------------------------------ |
| Handles **external traffic** | Handles **internal discovery** |
| Security, routing, logging   | Service registration & lookup  |
| Used by **clients**          | Used by **microservices**      |



🛎 API Gateway = Reception Desk

    All visitors must enter here
    Checks ID card
    Directs visitors to correct department

📞 Eureka = Internal Telephone Directory

    Lists all departments with extension numbers
    Used only by employees inside building

    Client / UI
        |
        v
        API-GATEWAY
        |
        v
        EUREKA  ←----- ORDER-SERVICE
        ↑        PRODUCT-SERVICE
        |        PAYMENT-SERVICE

What happens step-by-step
All services register themselves in Eureka
API Gateway asks Eureka
👉 “Where is PRODUCT-SERVICE running?”

Eureka returns IP + Port
Gateway forwards request to that service

Ex:
Client calls:
http://localhost:8080/products/5
Gateway reads route:
lb://PRODUCT-SERVICE
Gateway asks Eureka:
PRODUCT-SERVICE → 10.20.30.5:8082
Then Gateway forwards:
http://10.20.30.5:8082/products/5
Client never knows real IP.

**Core Stack**

| Layer                      | Technology                     |
| -------------------------- | ------------------------------ |
| Language                   | **Java 17**                    |
| Framework                  | **Spring Boot 3.x**            |
| Build Tool                 | Maven / Gradle                 |
| Microservice Communication | REST, Feign                    |
| API Gateway                | **Spring Cloud Gateway**       |
| Service Discovery          | **Netflix Eureka**             |
| Load Balancing             | Spring Cloud LoadBalancer      |
| Fault Tolerance            | **Resilience4j**               |
| Async Messaging            | **Apache Kafka**               |
| Configuration              | Spring Cloud Config (optional) |
| Security                   | Spring Security + JWT          |
| Logging                    | Logback + Sleuth / Micrometer  |
| Monitoring                 | Prometheus + Grafana           |
| Containerization           | Docker                         |
| Orchestration              | Kubernetes                     |


                Feign                           RestTemplate                        WebClient
Feature	        Feign Client	                RestTemplate	                    WebClient
Style	        Declarative (Annotation-based)	Imperative (Manual HTTP calls)	    Reactive (Async, Non-blocking)
Ease of use	    ⭐⭐⭐⭐	                    ⭐⭐	                                ⭐⭐⭐
Boilerplate	    Minimal	                        More	                                Some
Ideal For	    Microservices calls	            Simple blocking calls	                Reactive APIs
Load balancing	Built-in (with Spring Cloud)	Manual	                                Manual


**What @FeignClient does**

@FeignClient is a Spring Cloud feature that allows one service to call another REST service just by defining a Java interface.

It automatically handles:

Building HTTP requests (GET, POST, PUT, DELETE, etc.)
Converting JSON → Java objects and vice versa
Handling paths, headers, and even load balancing (if configured)

**Where You Can Use Feign Client**

Writing REST call logic using RestTemplate or WebClient everywhere becomes repetitive.

Microservices communication
→ e.g., Product Service calling Category Service.

Service registry + discovery (Eureka)
→ When services are registered in Eureka, Feign can find them by name instead of fixed URLs.

Example:
@FeignClient(name = "category-service")  // no URL needed if discovered via Eureka

API Gateways
→ Internal services behind a gateway can call each other using Feign for integration.

**When RestTemplate is better than Feign**

    ✅ A. When you need dynamic or user-defined URLs
    Feign clients are meant for fixed or service-discovery-based endpoints declared in advance.
    
    If you need to call different URLs based on runtime input, Feign doesn’t work as nicely.

Use‑Case	                                                            Prefer
Microservice → Microservice call (fixed URLs or service discovery)	🟢 Feign Client
External / third‑party REST API call	                            🟢 RestTemplate or WebClient
Dynamic runtime URL or method	                                    🟢 RestTemplate
Need full HTTP control	                                            🟢 RestTemplate
Want minimal boilerplate + declarative calls	                    🟢 Feign
Reactive / asynchronous API calls	                                🟢 WebClient


