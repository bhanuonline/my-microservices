# Observer Pattern

---

## What Is the Observer Pattern?

The **Observer Pattern** is a behavioral design pattern where one object (the **Subject**) maintains a list of dependents (**Observers**) and automatically notifies them when its state changes.

> It defines a **one-to-many** dependency — one subject, many watchers.

---

## When to Use It

- Event handling systems (UI button clicks, form changes)
- Real-time data feeds (stock prices, sensors, live scores)
- MVC architecture — Model notifies View of data changes
- Publish/subscribe messaging systems

---

## How to Identify the Roles

Ask yourself three questions:

| Question | Maps To |
|---|---|
| **WHO** is being watched? | → **Subject** |
| **WHO** is watching? | → **Observer** |
| **WHAT** change triggers notify? | → **Event** |

---

## Implementation Steps

```
STEP 1 → Observer Interface        (what observers must do)
STEP 2 → Subject Interface         (what the subject must do)
STEP 3 → ConcreteSubject class     (the thing being watched)
STEP 4 → ConcreteObserver class    (the watcher)
STEP 5 → Main class                (connect and test)
```

### Key Rules
- **Interfaces** → zero variables (contracts only)
- **Subject class** → holds the list, state, and its own name
- **Observer class** → holds only its own identity variables
- Always ask: *"Who is RESPONSIBLE for this data?"*

---

## Real-World Analogy — YouTube

```
REAL WORLD                  PATTERN
─────────────────────────────────────────
YouTube Channel         →   Subject
  └─ has subscribers        └─ List<Observer>
  └─ uploads videos         └─ state change
  └─ notifies everyone      └─ notifyAll()

Viewer (Alice, Bob...)  →   Observer
  └─ gets notification      └─ update()
  └─ reacts to it           └─ your logic inside update()
```

> The one who **acts** = Subject  
> The one who **reacts** = Observer

---

## Observer Pattern in Spring

Spring uses the Observer pattern in **3 key places**:

1. **Application Events** → `@EventListener`
2. **Spring Security** → Authentication events
3. **Transaction Events** → `@TransactionalEventListener`

### Mapping: Pure Pattern → Spring Equivalent

| Your Observer Pattern | Spring Equivalent |
|---|---|
| `Subject` interface | `ApplicationEventPublisher` |
| `notifyAllObservers()` | `publisher.publishEvent()` |
| `Observer` interface | `@EventListener` annotation |
| `update()` method | your listener method |
| `ConcreteObserver` class | `@Component` listener class |

---

## `@TransactionalEventListener` — Deep Dive

### The Problem with `@EventListener`

Normal `@EventListener` fires **immediately** when the event is published — even if the database transaction later fails.

**❌ Wrong Timeline:**
```
Save order      ✅
Publish event   ✅
Email sent      ✅  ← already fired!
DB crashes      💥
Rollback        ❌  order deleted from DB

Result: Email sent for an order that doesn't exist!
```

**✅ Correct Timeline (with @TransactionalEventListener):**
```
Save order         ✅
Publish event      ✅  ← registered, but WAITING
DB crashes         💥
Rollback           ❌
Email?             🚫 NEVER FIRES

Result: No email for a failed order. Correct!
```

---

### 4 Transaction Phases

| Phase | When It Fires | Use Case |
|---|---|---|
| `AFTER_COMMIT` | Transaction succeeded | Send email ✅ |
| `AFTER_ROLLBACK` | Transaction failed | Send alert / log ✅ |
| `AFTER_COMPLETION` | Either way | Cleanup always ✅ |
| `BEFORE_COMMIT` | Just before commit | Validation ✅ |

### Quick Decision Rule

| Use | When |
|---|---|
| `@EventListener` | Transaction doesn't matter |
| `@TransactionalEventListener` | You **must** confirm DB succeeded before acting |

> Always use `@TransactionalEventListener(phase = AFTER_COMMIT)` for emails, SMS, payments — anything you **can't undo**.

---

## Transactions — Quick Recap

A **transaction** means: *"Do ALL these DB operations together, then commit. If one fails → undo all of them."*

### ACID Properties

| Letter | Property | Meaning |
|---|---|---|
| A | **Atomic** | All or Nothing |
| C | **Consistent** | Data always valid |
| I | **Isolated** | Transactions don't interfere |
| D | **Durable** | Committed data is permanent |

> `@Transactional` groups DB operations as "all or nothing."  
> `@TransactionalEventListener` needs `@Transactional` because it waits for a transaction **boundary**.

---

## Spring Security — Authentication Events

### What Happens During Login

```
User types username/password
        │
        ▼
Spring Security checks credentials
        │
        ├── ✅ Success → fires AuthenticationSuccessEvent
        │
        └── ❌ Failure → fires AuthenticationFailureEvent
```

Spring Security publishes these events automatically. You just write the listeners (Observers).

### Mapping

| Spring Security | Observer Pattern |
|---|---|
| `AuthenticationEventPublisher` | Subject |
| `AuthenticationSuccessEvent` | Event (data) |
| `AuthenticationFailureEvent` | Event (data) |
| `@EventListener` on your class | Observer (`update()`) |

---

## Annotation Cheat Sheet

```
Annotation                           When It Fires
────────────────────────────────────────────────────────
@EventListener                   →   Immediately, no wait

@TransactionalEventListener      →   Waits for transaction
  phase = AFTER_COMMIT           →   Only on success
  phase = AFTER_ROLLBACK         →   Only on failure
  phase = AFTER_COMPLETION       →   Always (success or fail)
  phase = BEFORE_COMMIT          →   Just before commit
```