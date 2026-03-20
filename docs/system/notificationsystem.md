Notification System

1️⃣ Requirements
1.1 Functional Requirements (FR)

| ID   | Requirement                                                 |
| ---- | ----------------------------------------------------------- |
| FR-1 | Send notifications via **SMS, Email, Push**                 |
| FR-2 | Support **sync (API)** and **async (event/Kafka)** requests |
| FR-3 | Support **retry on failure**                                |
| FR-4 | Support **fallback** (SMS → Email → Push)                   |
| FR-5 | Channel-specific payload construction                       |
| FR-6 | Vendor abstraction (Twilio, AWS SES, Firebase, etc.)        |
| FR-7 | Support high throughput (millions/day)                      |
| FR-8 | Observability (logs, metrics, tracing)                      |

1.2 Non-Functional Requirements (NFR)

| Category        | Requirement                                |
| --------------- | ------------------------------------------ |
| Scalability     | Horizontally scalable workers              |
| Availability    | ≥ 99.9%                                    |
| Performance     | < 200 ms orchestration latency             |
| Reliability     | Retry + DLQ                                |
| Extensibility   | Add new channel/vendor without code change |
| Loose Coupling  | No channel/vendor logic in core            |
| Fault Isolation | One vendor failure must not affect others  |
| Security        | Auth, rate limiting (via gateway)          |

2️⃣ High-Level Architecture (HLD)

Clients / Services
↓
API Gateway (Infra)
↓
Notification Service

2.2 High-Level Components
Entry Points
↓
Notification Ingress
↓
Workflow Engine (Orchestrator)
↓
Channel Router
↓
Message Builder Pipeline
↓
Outbound Dispatcher
↓
Gateway Adapters (SMS / Email / Push)

2.3 Responsibilities (HLD View)

| Component        | Responsibility       |
| ---------------- | -------------------- |
| Entry Points     | Accept API / Events  |
| Ingress          | Normalize input      |
| Orchestrator     | Workflow control     |
| Router           | Channel selection    |
| Builder Pipeline | Payload creation     |
| Dispatcher       | Async delivery       |
| Gateway Adapter  | External integration |

2.4 Failure & Retry (HLD)
Failure → Retry Topic → Delay → Reprocess
Retries Exhausted → Fallback Topic
Permanent Failure → DLQ

3️⃣ Low-Level Design (LLD)
Sync
POST /notifications/send

Async
Kafka Consumer
SQS Listener

📌 Entry points do NO business logic

3.2 Notification Ingress

class NotificationIngress {
NotificationContext normalize(Request req)
}

Responsibility:
Validate schema
Add metadata (requestId, timestamp)

3.3 Workflow Engine (Orchestrator)
class NotificationOrchestrator {
void orchestrate(NotificationContext ctx)
}
Responsibilities:
Retry orchestration
Fallback decision
Stateless

❌ Does not build payload
❌ Does not talk to vendors

3.4 Channel Router (Loose Coupling)
interface ChannelHandler {
NotificationType type();
void handle(NotificationContext ctx);
}

class ChannelRouter {
ChannelHandler route(NotificationType type);
}

📌 Open-Closed Principle compliant
📌 No if-else explosion


3.5 Message Builder Pipeline (LLD Highlight)
interface BuildStep {
void execute(NotificationContext ctx);
}

Pipeline steps:
ValidationStep
TemplateStep
LocalizationStep
MetadataStep

📌 Builder Pattern → Pipeline Pattern (more scalable)

3.6 Outbound Dispatcher

interface OutboundDispatcher {
void dispatch(NotificationContext ctx);
}

Responsibilities:
Vendor selection
Circuit breaker
Async enqueue

3.7 Gateway Adapters (Integration Layer)

interface SmsGateway {
void send(SmsPayload payload);
}

implementations:
TwilioSmsGateway
AwsSmsGateway

📌 Vendor swapping via config

3.8 Retry & Fallback (LLD)

| Scenario          | Action      |
| ----------------- | ----------- |
| Timeout           | Retry Topic |
| 5xx               | Retry       |
| Retries exhausted | Fallback    |
| Permanent failure | DLQ         |

4️⃣ Deployment View

Notification Service (N instances)
├─ API Pod
├─ Worker Pod
├─ Retry Worker
└─ Fallback Worker

5️⃣ Design Patterns Used (Interview Gold)

| Pattern                 | Usage             |
| ----------------------- | ----------------- |
| Builder / Pipeline      | Payload creation  |
| Factory / Router        | Channel selection |
| Strategy                | Vendor selection  |
| Adapter                 | External gateways |
| Retry + Circuit Breaker | Resilience        |
| Event-driven            | Scalability       |


2️⃣ Step 1: Create the Skeleton Project
Tech Stack (Recommended)

Spring Boot
Java 17+
Maven / Gradle
Lombok
Kafka (later)
Resilience4j (later)

Initial Modules (Single Repo)
notification-service
├── api
├── core
├── orchestration
├── routing
├── builder
├── dispatch
├── integration
└── config

👉 Start monolith-style, split later if needed.

3️⃣ Step 2: Define Core Contracts FIRST (Key Rule)
Why?

Interfaces stabilize architecture.

Must-have Interfaces (Create Day 1)
NotificationOrchestrator
ChannelRouter
BuildPipeline
OutboundDispatcher
NotificationSender


📌 No implementation yet

4️⃣ Step 3: Build ONE Happy Path (Email Only)
Flow to Implement
REST API → Orchestrator → Router → Builder → Sender

What to Skip Initially

❌ Retry
❌ Fallback
❌ Kafka

5️⃣ Step 4: Implement Components Incrementally
4.1 Controller
POST /notifications


Calls orchestrator.

4.2 Orchestrator (Simple First)
orchestrate(ctx) {
router.route(ctx.type()).handle(ctx);
}

4.3 Channel Handler (Email)
EmailChannelHandler

Calls pipeline
Calls dispatcher

4.4 Builder Pipeline (Minimal)
Steps:
ValidateStep
TemplateStep

7️⃣ Step 6: Add Retry (Async Only)
DO NOT use Thread.sleep ❌

Use:
Kafka / SQS
Retry Topic
Retry Consumer
Fail → retry-topic → delay → reprocess

8️⃣ Step 7: Add Fallback (Config Driven)
fallback:
SMS: EMAIL

9️⃣ Step 8: External Integrations (Last)
Plug real Email gateway
Wrap with Adapter
Add circuit breaker