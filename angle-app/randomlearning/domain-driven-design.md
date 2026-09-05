# Domain-Driven Design (DDD) — Complete Reference & Interview Guide

A comprehensive reference on DDD: what it is, the four core building blocks (Entity, Value Object, Aggregate, Bounded Context), the supporting concepts (Domain Service, Event, Repository, Factory, Application Service), strategic patterns, worked examples, anti-patterns, and 40+ interview questions.

---

## What is DDD?

**Domain-Driven Design** was introduced by **Eric Evans** in his 2003 book *"Domain-Driven Design: Tackling Complexity in the Heart of Software."* It's a philosophy and toolkit for building software that:

> **Structures code around the business domain — not around technical concerns like databases, frameworks, or UI layers.**

The single most important idea:

> **The code should look like the business, not the database.**

If a business person and a developer look at the same class, they should recognize the same concepts.

## Why DDD matters

### Without DDD

- **Anemic domain model** — classes are dumb data holders with getters/setters; all logic lives in "service" classes
- **God objects** — one giant `Customer` class shared across every team, with 200 fields (half unused per context)
- **Scattered business rules** — the rule "an order can't be canceled after shipping" lives in 5 different services
- **Frequent bugs** — "we forgot to update X when Y changed"
- **Fragile refactors** — every change ripples across the codebase

### With DDD

- **Rich domain model** — business logic lives with the data it operates on
- **Small, focused models per context** — the sales team's `Customer` differs from the billing team's
- **Invariants enforced at aggregate roots** — impossible to violate from outside
- **Contexts evolve independently** — changes stay local
- **Shared vocabulary** — devs, product, and business use the same words

## The two levels of DDD

**Tactical DDD** — the code-level building blocks (Entity, Value Object, Aggregate, etc.)

**Strategic DDD** — the whole-system building blocks (Bounded Context, Context Map, Ubiquitous Language)

Most developers start with tactical and gradually adopt strategic thinking.

---

# PART 1 — TACTICAL DDD

## 1. Entity

### Definition

An **Entity** is a domain object with a stable **identity** that persists over time, even if all its attributes change.

Equality is by **identity**, not by attribute values.

### Java example

```java
public class Customer {
    private final CustomerId id;           // identity — never changes
    private String name;
    private EmailAddress email;
    private Address address;

    public Customer(CustomerId id, String name, EmailAddress email) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.email = Objects.requireNonNull(email);
    }

    // Business behavior — not just getters/setters
    public void changeName(String newName) {
        if (newName == null || newName.isBlank())
            throw new IllegalArgumentException("Name required");
        this.name = newName;
    }

    public void moveTo(Address newAddress) {
        this.address = newAddress;
    }

    public CustomerId id() { return id; }
    public String name()   { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer c)) return false;
        return this.id.equals(c.id);           // ONLY identity matters
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
```

### Key properties

| Property | Description |
|---|---|
| Identity | Stable, unique — usually a `Long` or `UUID` |
| Equality | By `id`, never by fields |
| Mutability | May be mutable (attributes change over time) |
| Lifecycle | Tracked — created, modified, archived, deleted |
| Behavior | Has business methods, not just getters/setters |

### Real-world entities

- `User`, `Order`, `Product`, `Invoice`, `Account`
- Anything with a database primary key that means "this specific thing"

### Analogy

You (as a person) are an entity. Your name, address, hair color, weight can all change — but you're still you. Your identity persists.

### Wrong — anemic entity

```java
public class Customer {
    private Long id;
    private String name;
    private String email;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    // ... more getters/setters
}
```

This is a data holder, not a domain object. Business logic lives elsewhere — in services — and that's an **anemic domain model**.

### Right — rich entity

```java
public class Customer {
    private final CustomerId id;
    private String name;
    private EmailAddress email;
    private CustomerStatus status;

    public void promote() {
        if (status != CustomerStatus.STANDARD)
            throw new IllegalStateException("Only STANDARD can be promoted");
        this.status = CustomerStatus.PREMIUM;
    }

    public boolean canReceive(Newsletter n) {
        return status.acceptsMarketing() && email.isVerified();
    }
}
```

Behavior lives *with* the data — that's the whole point.

---

## 2. Value Object

### Definition

A **Value Object** has **no identity** of its own. Two value objects are equal if their fields are equal — that's all that matters.

### Java example — using records (perfect fit)

```java
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        if (amount.signum() < 0)
            throw new IllegalArgumentException("Money cannot be negative");
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money times(int factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }

    public static Money zero(Currency c) {
        return new Money(BigDecimal.ZERO, c);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency))
            throw new IllegalArgumentException("Currency mismatch");
    }
}
```

### Key properties

| Property | Description |
|---|---|
| Identity | None |
| Equality | By all fields |
| Mutability | **Always immutable** |
| Lifecycle | None — created and thrown away freely |
| Instantiation | Freely — no ID generation, no DB fetch |

### More value object examples

```java
public record Address(String street, String city, String zip, Country country) {}

public record DateRange(LocalDate from, LocalDate to) {
    public DateRange {
        if (from.isAfter(to)) throw new IllegalArgumentException();
    }
    public boolean contains(LocalDate d) { return !d.isBefore(from) && !d.isAfter(to); }
    public long days() { return ChronoUnit.DAYS.between(from, to); }
}

public record EmailAddress(String value) {
    public EmailAddress {
        if (!value.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"))
            throw new IllegalArgumentException("Invalid email: " + value);
    }
    public String domain() { return value.substring(value.indexOf('@') + 1); }
}

public record PhoneNumber(String countryCode, String number) {}

public record Coordinates(double lat, double lng) {
    public double distanceTo(Coordinates other) { /* Haversine */ }
}

public record Percentage(BigDecimal value) {
    public Percentage {
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException();
    }
}
```

### Analogy

A $10 bill. You don't care **which** $10 bill you have — any $10 bill is as good as any other. They're interchangeable.

### The big insight — most entity fields should be value objects

Wrong:
```java
public class Customer {
    private String email;           // just a String
    private String phone;           // just a String
    private BigDecimal accountBalance;  // just a BigDecimal
}
```

Right:
```java
public class Customer {
    private EmailAddress email;     // validation + behavior
    private PhoneNumber phone;      // country code, formatting
    private Money accountBalance;   // currency-aware arithmetic
}
```

Now:
- Validation is enforced at construction — you can't have an invalid email
- Behavior lives with the type — `email.domain()`, not a util method
- The type expresses intent — `Money` is more meaningful than `BigDecimal`

### Entity vs Value Object

| Aspect | Entity | Value Object |
|---|---|---|
| Identity | Yes, stable ID | None |
| Equality by | ID | All fields |
| Mutability | May be mutable | Always immutable |
| Lifecycle | Tracked over time | No lifecycle |
| Storage | Usually own table with PK | Often embedded columns |
| Example | Customer, Order | Money, Address, Email |

**Rule of thumb:** if you'd tell two of them apart by asking "which one?" it's an entity. If they're interchangeable, it's a value object.

---

## 3. Aggregate

### Definition

An **Aggregate** is a cluster of related entities and value objects that must be treated as a **single unit** for data changes. It has one **Aggregate Root** — the only object outside the aggregate can reference.

### Key rules

1. **One root per aggregate** — external code only references the root
2. **Internal entities are not addressable from outside**
3. **All modifications go through the root** — the root enforces invariants
4. **The aggregate is the transactional boundary** — modify one aggregate per transaction
5. **Cross-aggregate references use IDs only** — never object references

### Full Java example — `Order` aggregate

```java
// Aggregate Root
public class Order {
    private final OrderId id;
    private final CustomerId customerId;    // reference to another aggregate — by ID
    private OrderStatus status;
    private final List<OrderLine> lines;
    private Address shippingAddress;
    private final Instant createdAt;

    public Order(OrderId id, CustomerId customerId, Address shipTo) {
        this.id = id;
        this.customerId = customerId;
        this.shippingAddress = shipTo;
        this.status = OrderStatus.DRAFT;
        this.lines = new ArrayList<>();
        this.createdAt = Instant.now();
    }

    // --- Business behavior (enforces invariants) ---

    public void addLine(ProductId product, int quantity, Money unitPrice) {
        requireStatus(OrderStatus.DRAFT);
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");

        lines.stream()
             .filter(l -> l.productId().equals(product))
             .findFirst()
             .ifPresentOrElse(
                 existing -> existing.increaseQuantity(quantity),
                 () -> lines.add(new OrderLine(product, quantity, unitPrice))
             );
    }

    public void removeLine(ProductId product) {
        requireStatus(OrderStatus.DRAFT);
        lines.removeIf(l -> l.productId().equals(product));
    }

    public void confirm() {
        requireStatus(OrderStatus.DRAFT);
        if (lines.isEmpty()) throw new IllegalStateException("Cannot confirm empty order");
        this.status = OrderStatus.CONFIRMED;
    }

    public void ship() {
        requireStatus(OrderStatus.CONFIRMED);
        this.status = OrderStatus.SHIPPED;
    }

    public void cancel() {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED)
            throw new IllegalStateException("Cannot cancel " + status + " order");
        this.status = OrderStatus.CANCELED;
    }

    // --- Computed queries ---

    public Money total() {
        return lines.stream()
                    .map(OrderLine::subtotal)
                    .reduce(Money.zero(Currency.USD), Money::plus);
    }

    // --- Controlled read access ---

    public OrderId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public OrderStatus status() { return status; }
    public List<OrderLine> lines() { return List.copyOf(lines); }   // defensive copy!

    private void requireStatus(OrderStatus expected) {
        if (this.status != expected)
            throw new IllegalStateException(
                "Expected " + expected + " but was " + status);
    }
}

// Internal entity — only reachable through the root
public class OrderLine {
    private final ProductId productId;
    private int quantity;
    private final Money unitPrice;

    OrderLine(ProductId productId, int qty, Money unitPrice) {   // package-private!
        this.productId = productId;
        this.quantity = qty;
        this.unitPrice = unitPrice;
    }

    void increaseQuantity(int by) { this.quantity += by; }
    public Money subtotal() { return unitPrice.times(quantity); }
    public ProductId productId() { return productId; }
    public int quantity()        { return quantity; }
    public Money unitPrice()     { return unitPrice; }
}

// Enums as value objects
public enum OrderStatus { DRAFT, CONFIRMED, SHIPPED, DELIVERED, CANCELED }
```

### Wrong — leaking internals

```java
// BAD — exposes mutable internal state
public List<OrderLine> getLines() { return lines; }

// caller code:
order.getLines().clear();   // bypasses invariants; empty confirmed order
order.getLines().add(new OrderLine(...));   // adds without checking status
```

### Right — controlled access via the root

```java
// Only expose via defensive copy
public List<OrderLine> lines() { return List.copyOf(lines); }

// Modifications only through methods that enforce invariants
order.addLine(productId, 2, price);   // enforces status check
```

### Why the "one aggregate per transaction" rule

If you modify multiple aggregates in one transaction, you're saying "they must stay consistent together" — which means they should probably be **one aggregate**. Keep aggregates small, and use eventual consistency between them via domain events.

**Bad:**
```java
@Transactional
public void placeOrder(...) {
    Order order = orderRepo.findById(id);
    Inventory inv = inventoryRepo.findById(productId);
    order.confirm();
    inv.reserve(qty);
    orderRepo.save(order);
    inventoryRepo.save(inv);       // two aggregates in one transaction
}
```

**Good:**
```java
@Transactional
public void placeOrder(...) {
    Order order = orderRepo.findById(id);
    order.confirm();
    orderRepo.save(order);
    eventPublisher.publish(new OrderConfirmed(order.id()));  // async
}

// Separate transaction, in another service or async handler:
@EventListener
public void on(OrderConfirmed event) {
    Inventory inv = inventoryRepo.findById(event.productId());
    inv.reserve(event.quantity());
    inventoryRepo.save(inv);
}
```

Eventual consistency, but each aggregate stays a clean transaction.

### Aggregate sizing rules

**Too big:** slow saves, contention on updates, hard to maintain invariants
**Too small:** business rules span aggregates, awkward event handling

**Guideline:** an aggregate should be as small as possible while still containing enough state to enforce the invariants you care about.

### Analogy — the wallet

An aggregate is like a **wallet**. The wallet is the root; the cards, cash, and receipts inside are its internals. You don't hand out an individual card and let a friend modify it independently — you go through the wallet.

---

## 4. Supporting concepts

### 4.1. Domain Service

Behavior that doesn't naturally belong on any entity or value object. Usually operates on **multiple aggregates**.

```java
public class MoneyTransferService {   // domain service
    public void transfer(Account from, Account to, Money amount) {
        from.withdraw(amount);        // aggregate 1
        to.deposit(amount);           // aggregate 2
        // domain rule: transfers can't be split across currencies
    }
}
```

Not to be confused with an **application service** (which is thicker and orchestrates use cases). A **domain service** is stateless and lives in the domain layer.

### 4.2. Domain Event

Something the business cares about that happened.

```java
public record OrderConfirmed(
    OrderId orderId,
    CustomerId customerId,
    Money total,
    Instant occurredAt
) {}

public record CustomerRegistered(CustomerId id, EmailAddress email, Instant at) {}

public record MoneyDeposited(AccountId id, Money amount, Instant at) {}
```

**Rules:**
- Named in past tense (`OrderConfirmed`, not `ConfirmOrder`)
- Immutable value objects
- Include only the data downstream consumers need
- Published by the aggregate when its state changes

Events are the primary way bounded contexts talk to each other without direct dependencies.

### 4.3. Repository

Abstraction over aggregate persistence. Only **aggregate roots** have repositories.

```java
public interface OrderRepository {
    Optional<Order> findById(OrderId id);
    List<Order> findByCustomer(CustomerId customerId);
    void save(Order order);
    void delete(OrderId id);
}
```

Concrete implementations live in the infrastructure layer (JPA, MongoDB, whatever) — the domain doesn't care.

**Wrong:** `OrderLineRepository` — `OrderLine` isn't an aggregate root; it's accessed through `Order`.

**Right:** query the whole order and access its lines: `order.lines()`.

### 4.4. Factory

For complex construction of aggregates.

```java
public class OrderFactory {
    public Order createFromCart(Customer customer, ShoppingCart cart, Address shippingAddress) {
        Order order = new Order(OrderId.newId(), customer.id(), shippingAddress);
        for (CartItem item : cart.items()) {
            order.addLine(item.productId(), item.quantity(), item.price());
        }
        return order;
    }
}
```

Use a factory when construction has non-trivial logic; otherwise, the aggregate root's constructor is fine.

### 4.5. Application Service

Thin orchestration layer. Loads aggregates, invokes domain methods, saves, publishes events. Contains **no business logic**.

```java
@Service
public class PlaceOrderApplicationService {
    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final DomainEventPublisher events;

    @Transactional
    public OrderId placeOrder(PlaceOrderCommand cmd) {
        Customer customer = customers.findById(cmd.customerId())
            .orElseThrow(() -> new CustomerNotFoundException(cmd.customerId()));

        Order order = new Order(OrderId.newId(), customer.id(), cmd.shippingAddress());
        for (var line : cmd.lines()) {
            order.addLine(line.productId(), line.quantity(), line.unitPrice());
        }
        order.confirm();

        orders.save(order);
        events.publish(new OrderConfirmed(order.id(), customer.id(), order.total(), Instant.now()));
        return order.id();
    }
}
```

This service does **no business logic** — it just wires up:
1. Load aggregates
2. Call domain methods (business logic lives inside them)
3. Save
4. Publish events

### 4.6. Ubiquitous Language

Everyone — developers, product managers, business stakeholders, testers — uses the **same words for the same concepts** within a bounded context. If the business says "confirm an order" and the code has `submitOrder()`, that's an ubiquitous-language violation. Rename it.

Signs it's working:
- New developers understand the codebase by reading domain classes
- Product specs use the same terms as the code
- Meetings don't get bogged down in translation

Signs it's broken:
- The code uses `Customer` but the business says "Account", "Client", "Member" interchangeably
- Business talks about "SKUs" but the code has `ProductVariant`
- Different services use different terms for the same thing

---

# PART 2 — STRATEGIC DDD

## 5. Bounded Context

### Definition

A **Bounded Context** is a **boundary within which a particular model applies** — a set of terms with precise, consistent meaning.

### The problem it solves

In a large business, the same word means different things to different teams:

- To **Sales**, a "Customer" is a company with a contract, lead source, rep, ARR
- To **Support**, a "Customer" is a user with tickets and satisfaction scores
- To **Billing**, a "Customer" is a legal entity with tax ID and invoices
- To **Shipping**, a "Customer" is a name + delivery address

If you try to model **one** `Customer` class that satisfies all four, you get a monstrous god-object with 200 fields, half unused per context.

### The DDD solution — one model per context

Each context gets **its own model** of what a "customer" is, with only the fields and behavior it needs:

```java
// Sales bounded context
package com.acme.sales.domain;
public class Customer {
    private final CustomerId id;
    private final String companyName;
    private final AccountManager rep;
    private final Money annualContractValue;
    private final LeadSource source;
    public void assignToAccountManager(...) {}
    public void escalateToVIP() {}
}

// Support bounded context
package com.acme.support.domain;
public class Customer {
    private final CustomerId id;
    private final String primaryContact;
    private final List<Ticket> openTickets;
    private final CSATScore csat;
    public boolean isEligibleForRefund() {}
}

// Billing bounded context
package com.acme.billing.domain;
public class Customer {
    private final CustomerId id;
    private final LegalEntityName legalName;
    private final TaxId taxId;
    private final PaymentMethod paymentMethod;
    public Invoice createInvoice(Money amount) {}
}
```

**Same `CustomerId`, three different models.** Each is smaller, focused, and easier to change. Teams work independently.

### How contexts communicate

- **Domain events** — published across a message bus (Kafka, RabbitMQ)
- **REST/gRPC APIs** — synchronous calls between contexts
- **Shared IDs** — `CustomerId` is a stable global identifier all contexts share

Contexts should **not** share database tables or domain models.

## 6. Context Map

A **Context Map** documents how bounded contexts relate to each other.

```
   ┌──────────────┐              ┌───────────────┐
   │    Sales     │──publishes──►│   Billing     │
   │  Context     │  customer.   │   Context     │
   │              │   signed     │               │
   └──────┬───────┘              └───────┬───────┘
          │                              │
          │ shares CustomerId            │ publishes
          ▼                              ▼
   ┌──────────────┐              ┌───────────────┐
   │   Support    │              │   Shipping    │
   │   Context    │              │   Context     │
   └──────────────┘              └───────────────┘
```

### Named relationships between contexts

| Pattern | Meaning |
|---|---|
| **Shared Kernel** | Two contexts share a small, tightly controlled common model. Requires coordination. |
| **Customer/Supplier** | Downstream context depends on upstream; upstream team accepts requirements from downstream. |
| **Conformist** | Downstream context conforms to upstream's model without negotiation (e.g., using a legacy system). |
| **Anti-Corruption Layer (ACL)** | A translation layer that isolates your domain from a messy external model. |
| **Open Host Service** | Upstream publishes a stable API/protocol many downstream contexts use. |
| **Published Language** | A well-documented format for inter-context communication (JSON schemas, protobuf). |
| **Partnership** | Two contexts must succeed or fail together; teams coordinate closely. |
| **Separate Ways** | Two contexts don't integrate at all — chosen deliberately. |

### Anti-Corruption Layer example

You're integrating with a 15-year-old CRM whose model doesn't match your domain. Instead of letting its weirdness leak in:

```java
public class CrmAntiCorruptionLayer {
    private final LegacyCrmClient crm;

    public Customer fetchCustomer(CustomerId id) {
        // legacy CRM returns some horrible XML DTO
        CrmXmlDto xml = crm.getCustomerXml(id.value());

        // translate to YOUR clean domain
        return new Customer(
            id,
            xml.getFullName(),
            new EmailAddress(xml.getEmailField()),
            translateAddress(xml.getShippingBlob())
        );
    }
}
```

Your domain code only sees `Customer`. The mess is quarantined in the ACL.

---

## 7. Bounded Context ≈ Microservice

This is the **fundamental architectural principle** for well-designed microservices: split by **business capability** (bounded context), not by **technical layer**.

### Wrong microservice split — by technical layer

- `UserService` (all user data)
- `OrderService` (all order-related things)
- `DatabaseService` (all DB access)
- `EmailService` (all email)

Symptom: every change touches multiple services; teams block each other.

### Right microservice split — by bounded context

- `SalesService`
- `SupportService`
- `BillingService`
- `ShippingService`

Each owns its own data, its own model. They talk via events or APIs.

---

# PART 3 — HOW THE PIECES FIT TOGETHER

```
┌───────────────────────────────────────────────────────────┐
│               BOUNDED CONTEXT (Sales)                     │
│                                                           │
│  ┌─────────────────────────────────────────────────┐      │
│  │              AGGREGATE (Order)                  │      │
│  │                                                 │      │
│  │  ┌───────────────┐   ┌───────────────┐          │      │
│  │  │   Aggregate   │   │    Entity     │          │      │
│  │  │     Root      │──►│  (OrderLine)  │          │      │
│  │  │   (Order)     │   └───┬───────────┘          │      │
│  │  └───────┬───────┘       │                      │      │
│  │          │               │                      │      │
│  │          ▼               ▼                      │      │
│  │  ┌───────────────┐   ┌───────────────┐          │      │
│  │  │  Value Object │   │  Value Object │          │      │
│  │  │  (OrderId)    │   │    (Money)    │          │      │
│  │  └───────────────┘   └───────────────┘          │      │
│  └─────────────────────────────────────────────────┘      │
│                                                           │
│  Other aggregates: Customer (root+entity), Product ...    │
│                                                           │
│  Repositories: OrderRepository, CustomerRepository ...    │
│  Domain services: PricingService, DiscountService ...     │
│  Application services: PlaceOrder, CancelOrder ...        │
│  Domain events: OrderConfirmed, OrderShipped ...          │
└───────────────────────────────────────────────────────────┘
             │              │
   publishes │              │ subscribes
             ▼              │
    ┌──────────────┐        │
    │   Billing    │────────┘
    │   Context    │
    └──────────────┘
```

---

# PART 4 — FULL WORKED EXAMPLE (E-COMMERCE)

Let's model a complete e-commerce ordering system using DDD.

## Value objects

```java
public record OrderId(UUID value) {
    public static OrderId newId() { return new OrderId(UUID.randomUUID()); }
}

public record CustomerId(UUID value) {
    public static CustomerId newId() { return new CustomerId(UUID.randomUUID()); }
}

public record ProductId(String sku) {}

public record Money(BigDecimal amount, Currency currency) {
    public Money { /* validation */ }
    public Money plus(Money other) { /* */ }
    public Money times(int n) { /* */ }
    public static Money zero(Currency c) { /* */ }
}

public record Address(String street, String city, String zip, Country country) {}

public record EmailAddress(String value) {
    public EmailAddress { if (!value.contains("@")) throw new IllegalArgumentException(); }
}

public record Quantity(int value) {
    public Quantity {
        if (value <= 0) throw new IllegalArgumentException("Quantity must be positive");
    }
    public Quantity plus(Quantity other) { return new Quantity(value + other.value); }
}
```

## Aggregate — Order

```java
public class Order {
    private final OrderId id;
    private final CustomerId customerId;
    private final List<OrderLine> lines;
    private OrderStatus status;
    private final Address shippingAddress;
    private final Instant createdAt;
    private final List<DomainEvent> events;   // events raised by this aggregate

    public Order(OrderId id, CustomerId customerId, Address shipTo) {
        this.id = id;
        this.customerId = customerId;
        this.shippingAddress = shipTo;
        this.status = OrderStatus.DRAFT;
        this.lines = new ArrayList<>();
        this.createdAt = Instant.now();
        this.events = new ArrayList<>();
    }

    public void addLine(ProductId productId, Quantity qty, Money unitPrice) {
        requireStatus(OrderStatus.DRAFT);
        lines.stream()
             .filter(l -> l.productId().equals(productId))
             .findFirst()
             .ifPresentOrElse(
                 existing -> existing.increaseQuantity(qty),
                 () -> lines.add(new OrderLine(productId, qty, unitPrice))
             );
    }

    public void confirm() {
        requireStatus(OrderStatus.DRAFT);
        if (lines.isEmpty()) throw new IllegalStateException("Empty order");
        this.status = OrderStatus.CONFIRMED;
        events.add(new OrderConfirmed(id, customerId, total(), Instant.now()));
    }

    public void ship(TrackingNumber tracking) {
        requireStatus(OrderStatus.CONFIRMED);
        this.status = OrderStatus.SHIPPED;
        events.add(new OrderShipped(id, tracking, Instant.now()));
    }

    public void cancel(String reason) {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED)
            throw new IllegalStateException("Cannot cancel " + status);
        this.status = OrderStatus.CANCELED;
        events.add(new OrderCanceled(id, reason, Instant.now()));
    }

    public Money total() {
        return lines.stream()
                    .map(OrderLine::subtotal)
                    .reduce(Money.zero(Currency.USD), Money::plus);
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }

    public OrderId id() { return id; }
    public OrderStatus status() { return status; }
    public List<OrderLine> lines() { return List.copyOf(lines); }

    private void requireStatus(OrderStatus expected) {
        if (this.status != expected)
            throw new IllegalStateException("Expected " + expected + " but was " + status);
    }
}
```

## Internal entity — OrderLine

```java
public class OrderLine {
    private final ProductId productId;
    private Quantity quantity;
    private final Money unitPrice;

    OrderLine(ProductId productId, Quantity qty, Money unitPrice) {   // package-private
        this.productId = productId;
        this.quantity = qty;
        this.unitPrice = unitPrice;
    }

    void increaseQuantity(Quantity by) { this.quantity = quantity.plus(by); }

    public Money subtotal() { return unitPrice.times(quantity.value()); }
    public ProductId productId() { return productId; }
    public Quantity quantity()   { return quantity; }
    public Money unitPrice()     { return unitPrice; }
}
```

## Domain events

```java
public sealed interface DomainEvent permits
    OrderConfirmed, OrderShipped, OrderCanceled {
    Instant occurredAt();
}

public record OrderConfirmed(OrderId orderId, CustomerId customerId,
                             Money total, Instant occurredAt) implements DomainEvent {}

public record OrderShipped(OrderId orderId, TrackingNumber tracking,
                           Instant occurredAt) implements DomainEvent {}

public record OrderCanceled(OrderId orderId, String reason,
                            Instant occurredAt) implements DomainEvent {}
```

## Repository interface

```java
public interface OrderRepository {
    Optional<Order> findById(OrderId id);
    List<Order> findByCustomer(CustomerId customerId);
    void save(Order order);
}
```

## Application service

```java
@Service
@Transactional
public class PlaceOrderApplicationService {
    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final ProductCatalog products;
    private final DomainEventPublisher publisher;

    public PlaceOrderApplicationService(OrderRepository o, CustomerRepository c,
                                        ProductCatalog p, DomainEventPublisher pub) {
        this.orders = o; this.customers = c; this.products = p; this.publisher = pub;
    }

    public OrderId execute(PlaceOrderCommand cmd) {
        Customer customer = customers.findById(cmd.customerId())
            .orElseThrow(() -> new CustomerNotFoundException(cmd.customerId()));

        Order order = new Order(OrderId.newId(), customer.id(), cmd.shippingAddress());
        for (var line : cmd.lines()) {
            Money price = products.priceOf(line.productId());
            order.addLine(line.productId(), new Quantity(line.qty()), price);
        }
        order.confirm();

        orders.save(order);
        order.pullEvents().forEach(publisher::publish);
        return order.id();
    }
}
```

## Command / query DTOs (application layer)

```java
public record PlaceOrderCommand(CustomerId customerId,
                                Address shippingAddress,
                                List<LineItem> lines) {
    public record LineItem(ProductId productId, int qty) {}
}
```

The result: a rich domain model that expresses the business, with concerns properly separated across layers.

---

# PART 5 — DDD-ADJACENT PATTERNS

## CQRS (Command Query Responsibility Segregation)

Reads and writes have very different requirements. **CQRS** separates them:

- **Command side** — modifies aggregate state; enforces invariants; small, focused
- **Query side** — reads optimized views; can join across aggregates; not aggregate-shaped

```java
// Write side (uses aggregates)
public class ConfirmOrderCommandHandler {
    public void handle(ConfirmOrderCommand cmd) {
        Order order = orders.findById(cmd.orderId()).orElseThrow();
        order.confirm();
        orders.save(order);
    }
}

// Read side (dedicated read model, denormalized)
public record OrderSummaryView(
    UUID orderId, String customerName, String status,
    BigDecimal total, LocalDateTime placedAt
) {}

public class OrderSummaryQueryHandler {
    public List<OrderSummaryView> handle(OrderSearchQuery q) {
        return jdbcTemplate.query("SELECT o.id, c.name, o.status, o.total, o.created_at " +
                                   "FROM order_summaries o JOIN customers c ON c.id = o.customer_id " +
                                   "WHERE o.customer_id = ?", ...);
    }
}
```

Pairs beautifully with DDD — CQRS respects the aggregate for writes, and lets reads be shaped for UI needs.

## Event Sourcing

Instead of storing the current state of an aggregate, store the **sequence of events** that led to it. Rebuild state by replaying events.

```java
public class Order {
    // state
    private OrderId id;
    private OrderStatus status;
    private List<OrderLine> lines;

    // rebuild from events
    public static Order rehydrate(List<DomainEvent> events) {
        Order o = new Order();
        events.forEach(o::apply);
        return o;
    }

    private void apply(DomainEvent event) {
        switch (event) {
            case OrderCreated c -> { this.id = c.id(); this.status = OrderStatus.DRAFT; ... }
            case LineAdded a -> { this.lines.add(...); }
            case OrderConfirmed c -> { this.status = OrderStatus.CONFIRMED; }
            case OrderShipped s -> { this.status = OrderStatus.SHIPPED; }
            // ...
        }
    }
}
```

Benefits: audit log for free, time travel, replay for new projections.
Costs: complexity, event schema evolution, projection rebuilding.

## Saga pattern

Long-running business processes that span multiple aggregates/contexts. Each step is a local transaction; failures trigger compensating actions.

```
1. Order placed          → publishes OrderPlaced
2. Payment authorized    → publishes PaymentAuthorized
3. Inventory reserved    → publishes InventoryReserved
4. Order confirmed       → publishes OrderConfirmed

Failure in step 3? → publish InventoryReservationFailed
Saga listens → publishes CancelPayment → refund customer
```

DDD says: use sagas instead of distributed transactions.

---

# PART 6 — COMMON ANTI-PATTERNS

## Anemic Domain Model

The #1 DDD anti-pattern. Entities are dumb data holders with getters/setters; all logic lives in "services."

**Symptom:**
```java
@Service
public class OrderService {
    public void confirmOrder(Long orderId) {
        Order order = repo.findById(orderId);
        if (order.getStatus().equals("DRAFT")) {
            if (order.getLines().isEmpty()) throw new IllegalStateException();
            order.setStatus("CONFIRMED");
            repo.save(order);
        }
    }
}
```

**Fix:** move the business logic **into** the entity:
```java
public class Order {
    public void confirm() {
        if (status != DRAFT) throw new IllegalStateException();
        if (lines.isEmpty()) throw new IllegalStateException();
        this.status = CONFIRMED;
    }
}
```

## God Aggregate

One aggregate that owns everything. Every change contends on the same lock.

**Fix:** split into multiple aggregates. Use events for cross-aggregate coordination.

## Chatty Aggregates (calling each other synchronously)

```java
public class Order {
    private Customer customer;
    public void confirm() {
        customer.awardLoyaltyPoints(...);   // reaches into another aggregate
    }
}
```

**Fix:** raise a `OrderConfirmed` event; the Customer aggregate handles it separately.

## Leaky Aggregate

Exposing mutable internal collections.
```java
public List<OrderLine> getLines() { return lines; }   // BAD
```

**Fix:** return defensive copy or unmodifiable view.

## Shared database across contexts

Multiple bounded contexts read/write the same tables. Every schema change breaks something unrelated.

**Fix:** each context owns its schema. Cross-context data flows via events or APIs.

## Big-ball-of-mud Ubiquitous Language

Using the same word for different things across contexts, or different words for the same thing.

**Fix:** deliberate glossary per context; term negotiation with the business.

## DDD-lite / cargo cult

Naming folders "domain" and using records for value objects, but no aggregate invariants, no domain events, no ubiquitous language work with the business.

**Fix:** DDD is a mindset and a process, not just class-naming conventions.

---

# INTERVIEW QUESTIONS

## Basic

**Q1. What is DDD?**
A software design approach that structures code around the business domain — the vocabulary, rules, and workflows the business cares about — rather than around technical concerns. Introduced by Eric Evans in 2003.

**Q2. What is the difference between an Entity and a Value Object?**
Entities have identity (a stable ID) — two entities with the same field values but different IDs are different. Value Objects have no identity — two VOs are equal if their fields are equal. Entities may be mutable; value objects are always immutable.

**Q3. What is an Aggregate?**
A cluster of related entities and value objects treated as one unit for data consistency. Has an Aggregate Root that's the only externally-referenced object. All modifications go through the root, which enforces invariants.

**Q4. What is a Bounded Context?**
A boundary within which a particular model applies. Same term (like "Customer") can mean different things in different contexts — each context has its own model.

**Q5. Give a real-world example of a Value Object.**
Money (amount + currency), Address (street/city/zip), EmailAddress (validated string), DateRange (from/to). Anything you'd swap freely as long as the value is the same.

**Q6. What is a Domain Event?**
A record of something meaningful that happened in the domain (e.g., `OrderConfirmed`, `PaymentReceived`). Immutable, past-tense, published by aggregates when state changes. Primary way bounded contexts communicate.

## Intermediate

**Q7. Why should aggregates reference each other by ID, not by object?**
To keep aggregates as independent transaction boundaries. Object references would tempt cross-aggregate mutations in one transaction, which violates the "one aggregate per transaction" rule and creates hidden coupling.

**Q8. What's the difference between a Domain Service and an Application Service?**
Domain Service: stateless business logic that doesn't fit on any entity/VO (e.g., money transfer between two Accounts). Lives in the domain layer. Application Service: orchestrates use cases — loads aggregates, calls domain methods, saves, publishes events. Contains no business logic itself.

**Q9. Why is Java's `record` a great fit for Value Objects?**
Records are immutable, provide value-based equals/hashCode automatically, and have compact constructors for validation. That's exactly what value objects need.

**Q10. What is Ubiquitous Language?**
Shared vocabulary between developers and business stakeholders within a bounded context. Same words mean the same things everywhere — code, meetings, docs, tickets.

**Q11. Should I have a Repository for OrderLine?**
No. Only aggregate roots have repositories. `OrderLine` is internal to the `Order` aggregate — you load it via `orderRepo.findById(id).lines()`.

**Q12. How do bounded contexts communicate?**
Domain events (async, via message bus), REST/gRPC APIs (sync), or shared IDs. Never by sharing database tables or domain models.

**Q13. What's an Anti-Corruption Layer?**
A translation layer between your domain and a messy external system (legacy CRM, third-party API). It converts their model to yours, preventing their weirdness from leaking into your domain.

**Q14. What's the difference between an Anemic and Rich Domain Model?**
Anemic: entities are data holders with getters/setters; logic lives in services. Rich: entities have business behavior methods; logic lives with the data. Anemic is a DDD anti-pattern.

## Senior

**Q15. How do you decide aggregate boundaries?**
Ask: what invariants must always hold together? If a rule spans multiple entities, they belong in the same aggregate. Aggregates should be as small as possible while still enforcing their invariants — bigger aggregates mean bigger transactions and more contention.

**Q16. Explain the trade-offs between big and small aggregates.**
Big: strong consistency guarantees, but slow saves, lock contention, hard to scale. Small: fast, scalable, easy to lock, but rules that span aggregates need eventual consistency (via events) and compensating actions on failure.

**Q17. What's the relationship between DDD and microservices?**
A bounded context is the ideal microservice boundary. Well-designed microservices split by business capability (context), not by technical layer. Each service owns its model, its data, and communicates via events/APIs — mirroring DDD's context isolation.

**Q18. Explain CQRS. Why does it pair well with DDD?**
CQRS separates write models (commands, aggregates, invariants) from read models (queries, denormalized views). It respects DDD's aggregate boundaries on writes and lets reads be shaped for UI needs without contaminating the domain model.

**Q19. What is Event Sourcing? Trade-offs?**
Storing the sequence of events that led to an aggregate's state instead of the current state. Rebuild state by replay. Benefits: audit log, time travel, replay for new projections. Costs: complexity, event schema evolution, projection rebuilding.

**Q20. Why avoid distributed transactions between aggregates?**
Distributed transactions (2PC) are slow, block resources, and fail badly under network partitions. Prefer the Saga pattern — chain of local transactions + compensating actions on failure. Eventual consistency, better availability.

**Q21. How do you handle referential integrity across aggregates?**
You don't — the database doesn't enforce it across aggregate boundaries. Instead, ensure the aggregate holding the reference (via ID) handles gracefully when the referenced aggregate is missing (deleted, not yet created).

**Q22. Should aggregate roots hold references to external services (like an EmailSender)?**
No. Aggregates should be pure domain logic — no infrastructure dependencies. Publish domain events; let application services or event handlers do the "send email" step.

**Q23. When would you NOT use DDD?**
Simple CRUD apps with minimal business logic (DDD is overkill), teams unfamiliar with the domain (DDD requires deep business collaboration), prototypes/MVPs (get it working first), data-processing pipelines (data-oriented modeling fits better).

## Expert

**Q24. What's a "polyglot persistence" and how does DDD enable it?**
Different bounded contexts using different storage technologies (relational DB for Orders, graph DB for Recommendations, search index for Products). DDD makes this natural because each context owns its data with no cross-context DB sharing.

**Q25. Explain the "Repository" pattern vs a JPA `EntityManager`.**
JPA's `EntityManager` is a persistence abstraction — CRUD-focused. A DDD Repository is a **domain** abstraction — it returns aggregates ready to work with, not raw entities. It hides persistence details entirely; the domain doesn't know JPA exists.

**Q26. How does DDD handle "optimistic vs pessimistic" locking on aggregates?**
Typically optimistic locking on the aggregate root via a version column. Concurrent modifications throw `OptimisticLockException`. Pessimistic locks are avoided — they hurt throughput and violate the "no cross-aggregate transactions" principle.

**Q27. What's the relationship between DDD and hexagonal / clean architecture?**
Hexagonal ("ports and adapters") gives you the layer structure (domain → application → infrastructure). DDD tells you what belongs in the domain layer (aggregates, VOs, events). They're complementary — hex is *where*, DDD is *what*.

**Q28. Explain "eventual consistency" and its practical implications.**
Aggregate A commits, then publishes an event; aggregate B eventually reacts. Between commit and reaction, the system is inconsistent. UIs must handle this (show "processing..."), and business logic must be idempotent to survive retries.

**Q29. How do you version domain events?**
Never delete a field; add new fields as nullable. Add a version number to the event. Consumers handle old and new versions. For big shape changes, publish a new event type; keep publishing the old one for compatibility until consumers migrate.

**Q30. What's the difference between a Domain Event and an Integration Event?**
Domain events describe what happened in the domain, usually consumed inside the same bounded context. Integration events are the subset published across bounded contexts — often with stable schemas, versioning, and a message bus. Some architectures separate them; others treat all domain events as integration events.

---

# DEBUG SCENARIOS — spot the DDD violation

## D1. What's wrong here?

```java
@Entity
public class Order {
    private Long id;
    private String status;
    private List<OrderLine> lines;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<OrderLine> getLines() { return lines; }
    public void setLines(List<OrderLine> lines) { this.lines = lines; }
}

@Service
public class OrderService {
    public void confirmOrder(Long orderId) {
        Order order = repo.findById(orderId);
        if (order.getStatus().equals("DRAFT") && !order.getLines().isEmpty()) {
            order.setStatus("CONFIRMED");
            repo.save(order);
        }
    }
}
```

**Violation:** Anemic Domain Model. Business logic (transition rules, empty check) lives in the service instead of the aggregate.

**Fix:**
```java
public class Order {
    public void confirm() {
        if (status != DRAFT) throw new IllegalStateException();
        if (lines.isEmpty()) throw new IllegalStateException("Empty order");
        this.status = CONFIRMED;
    }
}
```

---

## D2. What's wrong here?

```java
public class Order {
    private Customer customer;             // holds reference to another aggregate
    private List<Product> products;        // holds references to Product aggregates

    public void confirm() {
        customer.awardLoyaltyPoints(total());  // modifying another aggregate
        products.forEach(p -> p.decrementStock(1));  // modifying more aggregates
    }
}
```

**Violation:** Cross-aggregate references + modifying multiple aggregates in one operation. Two aggregates now share a transaction, breaking the "one aggregate per transaction" rule.

**Fix:** reference other aggregates by ID; publish domain events for cross-aggregate coordination:
```java
public class Order {
    private CustomerId customerId;
    public void confirm() {
        this.status = CONFIRMED;
        raise(new OrderConfirmed(id, customerId, total()));
    }
}
// Elsewhere:
@EventListener
public void on(OrderConfirmed e) { customerRepo.findById(e.customerId()).awardPoints(e.total()); }
```

---

## D3. What's wrong here?

```java
public class Order {
    // ...
    public List<OrderLine> getLines() { return lines; }
}

// Client code
order.getLines().add(new OrderLine(...));   // bypassing confirmation status checks
order.getLines().clear();                    // empty order that isn't cancelled
```

**Violation:** Leaky aggregate — internal collection exposed for mutation, invariants bypassed.

**Fix:** return defensive copy; modifications only through methods:
```java
public List<OrderLine> lines() { return List.copyOf(lines); }
public void addLine(...) { /* enforces status */ }
```

---

## D4. What's wrong here?

```java
public class Customer {
    private CustomerId id;
    private String companyName;
    private BigDecimal contractValue;
    private List<Ticket> supportTickets;
    private CSATScore csat;
    private TaxId taxId;
    private PaymentMethod paymentMethod;
    private String shippingAddress;
    private String deliveryInstructions;
    // ...60 more fields for sales, support, billing, shipping
}
```

**Violation:** God object across bounded contexts. Sales, Support, Billing, Shipping all crammed into one class.

**Fix:** separate `Customer` in each bounded context, sharing only `CustomerId`.

---

## D5. What's wrong here?

```java
public class Money {
    private BigDecimal amount;
    private String currency;

    public Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    // getters
}
```

**Violation:** Mutable value object. Value objects must be immutable. Setter methods let callers mutate shared state, break equality assumptions, and violate immutability guarantees.

**Fix:**
```java
public record Money(BigDecimal amount, Currency currency) {
    public Money { /* validation */ }
    public Money plus(Money other) { return new Money(amount.add(other.amount), currency); }
    // NO setters. Operations return NEW instances.
}
```

---

## D6. What's wrong here?

```java
@Service
public class OrderService {
    @Autowired private OrderRepository orders;
    @Autowired private OrderLineRepository lines;    // separate repo for OrderLine
    @Autowired private OrderStatusHistoryRepository history;   // and for history

    public void addLine(Long orderId, ProductId pid, int qty) {
        Order order = orders.findById(orderId);
        OrderLine line = new OrderLine(pid, qty);
        lines.save(line);            // saved independently
        order.getLines().add(line);
        orders.save(order);
    }
}
```

**Violation:** Repositories for non-root entities (`OrderLine`, `OrderStatusHistory`). Only aggregate roots should have repositories.

**Fix:**
```java
public interface OrderRepository {   // only for Order aggregate
    Optional<Order> findById(OrderId id);
    void save(Order order);   // saves the whole aggregate (lines included)
}
```

---

## D7. What's wrong here?

```java
public class Sales_Customer {
    // ...
    public void promoteToBillingTier() {
        BillingService billing = SpringContext.getBean(BillingService.class);
        billing.upgradeTier(this.id);   // reaching across contexts synchronously
    }
}
```

**Violation:** Bounded contexts should not directly invoke each other synchronously from within domain code. Communication via domain events (async, decoupled).

**Fix:**
```java
public class Sales_Customer {
    public void promote() {
        this.tier = PREMIUM;
        raise(new CustomerPromoted(id, tier));  // Billing subscribes elsewhere
    }
}
```

---

## D8. What's wrong here?

```java
public class Order {
    // ...
    public void confirm() {
        this.status = CONFIRMED;
        emailService.send(customer.getEmail(), "Order confirmed");   // side effect in domain
        auditLogger.log("Order " + id + " confirmed");
    }
}
```

**Violation:** Aggregate directly performing infrastructure operations (email, logging). Domain code should be pure — no side effects on external systems.

**Fix:** raise a domain event, let application service or event handler send the email:
```java
public class Order {
    public void confirm() {
        this.status = CONFIRMED;
        raise(new OrderConfirmed(id, customerId, ...));
    }
}
```

---

# QUICK-FIRE RAPID ROUND

| Question | Answer |
|---|---|
| Who coined DDD? | Eric Evans (2003) |
| What defines an Entity? | Identity (stable ID) |
| What defines a Value Object? | Value; no identity |
| What's an Aggregate Root? | The externally-addressable entry point of an aggregate |
| One aggregate per what? | Transaction |
| Cross-aggregate references use? | IDs, not object references |
| Repositories are for what? | Aggregate roots only |
| Domain events named in what tense? | Past tense |
| Best fit for Value Objects in Java? | `record` |
| Bounded context in microservices ≈ ? | One microservice |
| Two contexts share the model? | Anti-pattern |
| How do contexts communicate? | Events, APIs, shared IDs |
| Anemic domain model = ? | Business logic in services, not entities |
| Distributed transactions across aggregates? | Anti-pattern — use Sagas |
| Anti-Corruption Layer purpose? | Translate messy external models to your domain |
| Ubiquitous language shared with? | Business stakeholders |
| CQRS separates what? | Reads from writes |
| Event Sourcing stores what? | Sequence of events, not current state |
| Domain event vs Integration event? | Same-context vs cross-context published |
| Aggregate size guideline? | As small as invariants allow |

---

# ONE-SENTENCE SUMMARIES

- **DDD** — structure code around the business domain, not around technical concerns
- **Entity** — has identity; equality by ID; may be mutable
- **Value Object** — no identity; equality by value; always immutable
- **Aggregate** — cluster with a root; transaction boundary; root enforces invariants
- **Bounded Context** — boundary within which a specific model applies; separate context = separate model
- **Domain Service** — stateless business logic that doesn't fit on entity/VO
- **Domain Event** — past-tense fact about something that happened in the domain
- **Repository** — abstraction over aggregate persistence; roots only
- **Application Service** — thin orchestration; no business logic; loads aggregates and calls domain methods
- **Ubiquitous Language** — shared vocabulary across dev and business within a context
- **Context Map** — documents relationships between bounded contexts
- **Anti-Corruption Layer** — translation layer isolating your domain from messy external systems
- **CQRS** — separate read model from write model
- **Event Sourcing** — store events, not state; rebuild state by replay
- **Saga** — chain of local transactions with compensations, replacing distributed transactions

---

## The interview-safe summary you can drop verbatim

> Domain-Driven Design is Eric Evans' approach to structuring software around the business domain rather than around technical concerns. Its tactical building blocks are Entities (things with stable identity), Value Objects (immutable things defined by their value), Aggregates (clusters of entities and value objects treated as one unit for consistency, with an Aggregate Root as the sole external entry point), and their supporting concepts — Domain Services, Domain Events, Repositories, Factories, and Application Services. Strategically, DDD organizes the whole system into Bounded Contexts — separate models with their own vocabulary — connected via a Context Map and communicating through events or APIs. The core insight is that the same term ("Customer") means different things to different teams, and forcing them into one shared model creates god objects; giving each context its own model creates focused, independently-evolvable services. This maps naturally to microservices, pairs well with CQRS for read/write separation and Event Sourcing for audit and replay, and prevents the anti-pattern of anemic domain models where business logic gets scattered across "services" instead of living with the data.
