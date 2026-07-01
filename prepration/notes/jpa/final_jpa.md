# JPA Complete Notes

---

## 1. JPA is a Specification, Not an Implementation

> Think of JPA like a rulebook or a contract — it defines **what** should be done, but not **how** to do it.

| Layer | Role |
|-------|------|
| **JPA** (`jakarta.persistence`) | Standard / Rulebook |
| **Hibernate, EclipseLink, OpenJPA** | Implementations of the rulebook |
| **Your Java Code** | Consumes the standard |

> **Note:** Hibernate existed *before* JPA. JPA was actually inspired by Hibernate. Hibernate also has extra features beyond the JPA spec.

---

## 2. Spring Data JPA Layer Stack

```
Spring Data JPA       ← makes JPA even easier (repositories, auto queries)
       ↓
      JPA             ← specification
       ↓
   Hibernate          ← default implementation in Spring Boot
       ↓
   Database
```

### `spring-boot-starter-data-jpa` includes:
- **Spring Data JPA** — smart repository layer
- **Hibernate** — JPA implementation (the engine)
- **JPA (jakarta)** — the specification/contract

---

## 3. What Spring Data JPA Actually Does

### Plain JPA + Hibernate (manual)
```java
@PersistenceContext
EntityManager em;

public User findById(Long id) {
    return em.find(User.class, id);
}

public void save(User user) {
    em.persist(user);
}

public List<User> findAll() {
    return em.createQuery("SELECT u FROM User u", User.class).getResultList();
}
```

### Spring Data JPA (just declare the interface)
```java
public interface UserRepository extends JpaRepository<User, Long> {
    // save(), findById(), findAll(), delete() — ALL auto-generated
    // Zero implementation needed
}
```

### The Magic — Query Methods from Method Names
Spring Data JPA generates queries just from method names:
```java
findByEmail(String email)
findByAgeGreaterThan(int age)
findByNameAndCity(String name, String city)
```

---

## 4. All 3 Layers Working Together

```
Your Code (UserRepository.findByEmail())
              ↓
    Spring Data JPA     → generates query, manages repositories
              ↓
        JPA (spec)      → defines EntityManager, @Entity, @Query rules
              ↓
          Hibernate      → translates to real SQL, talks to DB
              ↓
          Database       → MySQL / PostgreSQL / etc.
```

---

## 5. Entity Mapping

### Relationship Ownership Rules

| Side | Annotation | DB Effect |
|------|------------|-----------|
| **"Many" side** | `@ManyToOne` + `@JoinColumn` | Has the FK column |
| **"One" side** | `@OneToMany` + `mappedBy` | No FK column, just a reference |

### Key Attributes

| Attribute | Meaning |
|-----------|---------|
| `mappedBy = "user"` | "I am NOT the owner — look at the `user` field in Order for the FK" |
| `CascadeType.ALL` | Save/delete parent → save/delete children too |
| `FetchType.LAZY` | Children NOT loaded until explicitly accessed (better performance) |

### Example
```java
@Entity
public class Department {
    @OneToMany(mappedBy = "department")
    private List<Employee> employees;
}

@Entity
public class Employee {
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;
}
```

### `@ManyToMany`
Requires a **separate join table** (unlike `@OneToMany` which uses a FK column).

---

## 6. Common Exceptions

### `LazyInitializationException`
**Cause:** `LAZY` association accessed after the Session/Transaction is closed.

```
Parent entity loaded → Session closes → Code accesses lazy object → BOOM
```

**Fix — preferred solutions:**
- `JOIN FETCH` in JPQL
- `@EntityGraph`
- DTO Projection

> **Avoid** changing `FetchType` to `EAGER` — it causes performance issues.

---

### `JdbcTypeRecommendationException`
**Cause:** Hibernate encounters a custom Java object and cannot map it to a DB column.

**Fix:** Use proper relationship annotations instead of `@Column`:
- `@ManyToOne`, `@OneToOne`, `@OneToMany`, `@ManyToMany`

---

## 7. Embeddables

### When to Use
| Use `@Embeddable` ✅ | Don't Use ❌ |
|----------------------|-------------|
| Fields logically belong together (street, city, state) | Need to search/filter independently |
| Reuse the group across multiple entities | Needs its own lifecycle (create/delete separately) |
| No need for a separate table | |

### How It Works
```java
@Embeddable
public class Address {
    private String street;
    private String city;
    private String state;
}

@Entity
public class User {
    @Embedded
    private Address address; // Two Java classes — ONE table
}
```

> `@Embeddable` = "I am just a group of fields, I have no table"  
> `@Embedded` = "Take that group and put it inside my table"

---

## 8. EntityManager & Entity Lifecycle

### What is EntityManager?
```
Your Java Code
      ↓
EntityManager    ← you talk to this
      ↓
  Hibernate
      ↓
  Database
```

### 4 Lifecycle States

```
new User()
    │
    ▼
TRANSIENT          ← Just a Java object, DB doesn't know it exists
    │
    │  em.persist()
    ▼
MANAGED ◄─────────────────────── em.merge()
    │                                 ▲
    │  auto-tracks changes            │
    │                                 │
    ├──── session closes ──────► DETACHED
    │                                 │
    │  em.remove()                    │
    ▼                                 │
REMOVED                               │
    │                                 │
    │  on commit → DELETE fires        │
    │                                 │
    └──── object becomes TRANSIENT ───┘
```

| State | Meaning |
|-------|---------|
| **TRANSIENT** | Just a Java object, DB has no idea it exists |
| **MANAGED** | JPA is watching — every change gets auto-saved |
| **DETACHED** | JPA stopped watching — changes are ignored |
| **REMOVED** | JPA will delete from DB on next commit |

### Dirty Checking — The Magic of MANAGED State
```java
@Transactional
public void updateName(Long id, String newName) {
    User user = userRepository.findById(id).get(); // user is MANAGED

    user.setName(newName);
    // No save() needed! JPA detects the change automatically.
    // UPDATE fires when @Transactional commits.
}
```

> `save()` in Spring Data JPA calls `persist()` for new objects and `merge()` for existing ones automatically.

---

## 9. `@PersistenceContext`

Injects an `EntityManager` — like `@Autowired` but for EntityManager.

| Feature | `@PersistenceContext` | `@Autowired` |
|---------|----------------------|--------------|
| Purpose | EntityManager only | Any Spring bean |
| Instance | New per transaction | Shared singleton |
| Lifecycle | Managed by JPA/Hibernate | Managed by Spring |
| Thread Safety | Thread-safe (via proxy) | Depends on bean |

```java
@PersistenceContext
private EntityManager entityManager;   // transaction-scoped

@Autowired
private UserService userService;       // singleton Spring bean
```

> In real projects, you rarely need this — Spring Data JPA handles it for you.

---

## 10. JPQL (JPA Query Language)

Queries Java **classes and fields** instead of DB tables and columns.

### Syntax
```
SQL:   SELECT * FROM table WHERE condition
JPQL:  SELECT u FROM User u WHERE condition
                    ↑
              Java class name (not table name)
```

### Common Query Examples
```java
@Query("SELECT u FROM User u WHERE u.name = :name")
@Query("SELECT u.name, u.email FROM User u")
@Query("SELECT u FROM User u WHERE u.age > :age AND u.name = :name")
@Query("SELECT u FROM User u ORDER BY u.age DESC")
@Query("SELECT u FROM User u WHERE u.name LIKE %:keyword%")
@Query("SELECT COUNT(u) FROM User u WHERE u.age > :age")
@Query("SELECT o FROM Order o WHERE o.user.id = :userId")
@Query("SELECT u FROM User u JOIN FETCH u.orders WHERE u.id = :id")

// UPDATE / DELETE — require @Modifying + @Transactional
@Modifying
@Transactional
@Query("UPDATE User u SET u.name = :name WHERE u.id = :id")

@Modifying
@Transactional
@Query("DELETE FROM User u WHERE u.age < :age")
```

---

## 11. Annotation Quick Reference

### JPA Annotations
```java
// Entity mapping
@Entity, @Table, @Id, @GeneratedValue, @Column, @Transient, @Embeddable, @Embedded

// Relationships
@OneToOne, @OneToMany, @ManyToOne, @ManyToMany, @JoinColumn, @JoinTable

// Lifecycle hooks
@PrePersist, @PostPersist, @PreUpdate, @PostUpdate, @PreRemove, @PostRemove

// Querying
JPQL, @NamedQuery, @PersistenceContext
```

### Spring Data JPA Annotations
```java
// Repository interfaces
JpaRepository           // most common — use this
CrudRepository          // basic CRUD only
PagingAndSortingRepository  // adds pagination

// Annotations
@Repository, @Query, @Modifying, @Transactional, @Param, @EnableJpaRepositories
```

### Hibernate Configuration (`application.properties`)
```properties
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

---

## 12. N+1 Problem & Fixes

### What is it?
```java
// ❌ N+1 — 1 query for authors + 1 query per author for books
List<Author> authors = em.createQuery("SELECT a FROM Author a").getResultList();
for (Author a : authors) {
    a.getBooks(); // new DB query each time!
}
```

### Fix 1 — JOIN FETCH
```java
List<Author> authors = em.createQuery(
    "SELECT a FROM Author a JOIN FETCH a.books"
).getResultList();
```

### Fix 2 — Named Entity Graph
```java
@NamedEntityGraph(name = "author-with-books",
    attributeNodes = @NamedAttributeNode("books"))
@Entity
public class Author { ... }

em.createQuery("SELECT a FROM Author a", Author.class)
    .setHint("javax.persistence.fetchgraph", em.getEntityGraph("author-with-books"))
    .getResultList();
```

### Fix 3 — Programmatic Entity Graph
```java
EntityGraph<Author> graph = em.createEntityGraph(Author.class);
graph.addAttributeNodes("books");

em.createQuery("SELECT a FROM Author a", Author.class)
    .setHint("javax.persistence.fetchgraph", graph)
    .getResultList();
```

| Approach | Best For |
|----------|----------|
| `JOIN FETCH` | Simple, one-off queries |
| Named Entity Graph | Reusable, declared on entity |
| Programmatic Entity Graph | Dynamic, built at runtime |

---

## 13. Caching

### First-Level Cache (Session Cache)

- Always **ON** — built into Hibernate
- Scoped to a **single Session** (one request/transaction)
- Destroyed when Session closes
- No configuration needed

```java
Session session = factory.openSession();
Author a1 = session.get(Author.class, 1L); // hits DB
Author a2 = session.get(Author.class, 1L); // hits L1 cache ✅
session.close();

Session session2 = factory.openSession();
Author a3 = session2.get(Author.class, 1L); // hits DB again (new session)
```

### Second-Level Cache (Shared Cache)

- Optional — must be configured
- Scoped to **SessionFactory** — shared across all sessions
- Survives session close
- Providers: EhCache, Redis, Caffeine, Hazelcast

```java
// Mark entity as cacheable
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Author { ... }
```

### Cache Concurrency Strategies

| Strategy | When to Use |
|----------|-------------|
| `READ_ONLY` | Immutable data — fastest |
| `NONSTRICT_READ_WRITE` | Rarely updated, slight stale risk |
| `READ_WRITE` | Transactional updates — safe |
| `TRANSACTIONAL` | Full ACID, JTA only |

### Comparison

| Feature | L1 Cache | L2 Cache |
|---------|----------|----------|
| Scope | Session | SessionFactory |
| Default | Always ON | OFF (opt-in) |
| Shared | No | Yes |
| Survives close | No | Yes |
| Config needed | No | Yes |
| Best for | Single request | Frequently read, rarely changed |

---

## 14. Optimistic vs Pessimistic Locking

### Optimistic Locking

- **No DB lock held** — assumes conflicts are rare
- Checks at **commit time** — throws exception if conflict detected
- Uses `@Version` field

```java
@Entity
public class Product {
    @Id private Long id;
    private int stock;

    @Version
    private int version; // Hibernate manages this automatically
}
```

Generated SQL:
```sql
UPDATE product
SET stock = ?, version = 2
WHERE id = 1 AND version = 1  -- fails if version changed
```

### Pessimistic Locking

- **Locks DB row immediately on read** — assumes conflicts are likely
- Other transactions must wait

```java
Product p = session.get(
    Product.class, 1L,
    LockModeType.PESSIMISTIC_WRITE
); // row locked until transaction ends
```

Generated SQL:
```sql
SELECT * FROM product WHERE id = 1 FOR UPDATE
```

### Comparison

| Feature | Optimistic | Pessimistic |
|---------|------------|-------------|
| DB lock held | No | Yes |
| Conflict detection | At commit | At read |
| Failure mode | Exception on commit | Waiting / timeout |
| Performance | Better (no waiting) | Lower (contention) |
| Risk | Stale data conflict | Deadlock possible |
| Use when | Reads > Writes | Writes > Reads |

> **Rule of thumb:**
> - Optimistic → user profiles, blog posts, low-conflict data
> - Pessimistic → bank transfers, inventory deduction, booking seats
> - Always add `@Version` — cheap insurance against lost updates

---

## 15. Inheritance Mapping Strategies

### SINGLE_TABLE

All subclasses in **one table**, with a discriminator column.

```java
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type")
public abstract class Payment { ... }

@Entity @DiscriminatorValue("CREDIT")
public class CreditCardPayment extends Payment { ... }

@Entity @DiscriminatorValue("PAYPAL")
public class PaypalPayment extends Payment { ... }
```

| id | amount | type | cardNumber | paypalEmail |
|----|--------|------|------------|-------------|
| 1 | 100.0 | CREDIT | 1234-5678 | null |
| 2 | 50.0 | PAYPAL | null | a@gmail.com |

### JOINED

Each class has its **own table**, joined via FK.

```java
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Payment { ... }
```

Tables: `PAYMENT`, `CREDIT_CARD_PAYMENT`, `PAYPAL_PAYMENT` — joined on query.

### TABLE_PER_CLASS

Each **concrete class** has its own full table (parent fields duplicated).

```java
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class Payment { ... }
```

Polymorphic queries use `UNION ALL` across tables.

### Comparison

| Feature | SINGLE_TABLE | JOINED | TABLE_PER_CLASS |
|---------|-------------|--------|-----------------|
| Tables | 1 | 1 per class | 1 per concrete class |
| Joins needed | None | Yes | None (but UNION) |
| Nullable cols | Yes | No | No |
| Normalization | No | Yes | Partial |
| Polymorphic query | Fast | Medium | Slow (UNION) |
| Best for | Performance | Clean schema | Independent subclasses |

> **Rule of thumb:**
> - `SINGLE_TABLE` → default choice, small/medium hierarchies
> - `JOINED` → large hierarchies, data integrity matters
> - `TABLE_PER_CLASS` → avoid unless subclasses are truly independent

---

---

# 🎯 Interview Questions — JPA / Hibernate / Spring Data JPA

> Organized by category. Each question includes what the interviewer is really testing and a sharp answer.

---

## 🔧 Category 1: Technical / Concept Level

---

**Q1. What is the difference between JPA, Hibernate, and Spring Data JPA?**

| | Role |
|--|------|
| JPA | Specification (interface/contract) — `jakarta.persistence` |
| Hibernate | Implementation of JPA — does the actual SQL work |
| Spring Data JPA | Abstraction on top of JPA — auto-generates repositories |

> JPA defines *what* to do. Hibernate defines *how* to do it. Spring Data JPA makes it so you barely write any code at all.

---

**Q2. What is the difference between `persist()`, `merge()`, `detach()`, and `remove()`?**

| Method | Works On | Effect |
|--------|----------|--------|
| `persist()` | TRANSIENT entity | Makes it MANAGED, will INSERT on commit |
| `merge()` | DETACHED entity | Returns a new MANAGED copy, schedules UPDATE |
| `detach()` | MANAGED entity | Removes from persistence context — changes ignored |
| `remove()` | MANAGED entity | Schedules DELETE on commit |

> `merge()` does NOT modify the passed object — it returns a new managed instance. Ignoring the return value is a classic bug.

---

**Q3. What is dirty checking? How does Hibernate know what changed?**

When an entity is **MANAGED**, Hibernate takes a snapshot of it at load time. At flush/commit, it compares the current state with the snapshot. If different → `UPDATE` is fired automatically.

```java
@Transactional
public void update(Long id) {
    User u = repo.findById(id).get(); // snapshot taken here
    u.setName("New Name");            // state changed
    // no save() needed — Hibernate compares and fires UPDATE
}
```

> No save() needed inside `@Transactional` for managed entities. Calling save() again is redundant but harmless.

---

**Q4. What is the difference between `FetchType.LAZY` and `FetchType.EAGER`?**

| | LAZY | EAGER |
|--|------|-------|
| When loaded | On access (proxy) | Immediately with parent |
| Default for `@OneToMany` | Yes | No |
| Default for `@ManyToOne` | No | Yes |
| Performance | Better | Risk of over-fetching |

> Always use LAZY by default. EAGER can silently load thousands of records.

---

**Q5. What is the difference between `@JoinColumn` and `mappedBy`?**

- `@JoinColumn` → "I own this relationship. The FK column is in MY table."
- `mappedBy` → "I do NOT own this. Look at the other side for the FK."

Only the owning side (`@JoinColumn`) controls the FK column in the database. If you forget `mappedBy`, JPA creates a **duplicate join table** — a common bug.

---

**Q6. What is the difference between `CrudRepository`, `JpaRepository`, and `PagingAndSortingRepository`?**

```
CrudRepository
    ↑ extends
PagingAndSortingRepository  (adds findAll(Pageable), findAll(Sort))
    ↑ extends
JpaRepository               (adds flush(), saveAll(), deleteInBatch(), etc.)
```

> Use `JpaRepository` in almost every case. It gives you the most.

---

**Q7. What does `@Transactional` actually do?**

It wraps the method in a transaction:
- Opens a transaction before the method
- Commits on success
- Rolls back on unchecked exception (`RuntimeException`)
- Does NOT rollback on checked exceptions by default

```java
@Transactional(rollbackFor = Exception.class) // to rollback on checked too
@Transactional(readOnly = true)               // optimization for read-only
```

> `readOnly = true` tells Hibernate to skip dirty checking — faster for reads.

---

**Q8. What is JPQL and how is it different from SQL?**

| | JPQL | SQL |
|--|------|-----|
| Operates on | Java entities & fields | DB tables & columns |
| Portable | Yes (DB-agnostic) | No (DB-specific syntax) |
| Knows relationships | Yes (`u.orders`) | No (needs explicit JOIN) |

```java
// JPQL — uses class name and field name
"SELECT u FROM User u WHERE u.email = :email"

// SQL — uses table name and column name
"SELECT * FROM users WHERE email = ?"
```

---

## 🏛️ Category 2: Architecture Level

---

**Q9. How would you design a system to avoid N+1 queries in a large-scale application?**

Layer-by-layer strategy:

1. **Default all associations to LAZY** — never load what you don't need
2. **Use JOIN FETCH / @EntityGraph** per use case — fetch only what the specific query needs
3. **Use DTO projections** — don't return full entities when only 2 fields are needed
4. **Enable Hibernate statistics in dev** — catch N+1 during development
5. **Use Batch fetching** as a fallback (`@BatchSize(size = 20)`)

```java
// DTO Projection — no entity loaded at all
@Query("SELECT new com.app.dto.UserDTO(u.id, u.name) FROM User u")
List<UserDTO> findAllAsDTO();
```

---

**Q10. When would you choose Optimistic vs Pessimistic locking in a real system?**

| Scenario | Choice | Why |
|----------|--------|-----|
| User editing their profile | Optimistic | Conflicts are rare |
| Flight seat booking | Pessimistic | Two users booking same seat simultaneously |
| Inventory deduction | Pessimistic | Race conditions cause overselling |
| Blog post update | Optimistic | Only one author edits at a time |
| Bank transfer | Pessimistic | Correctness > performance |

> In high-read, low-write systems → Optimistic. In high-write, high-contention → Pessimistic.

---

**Q11. When would you use Second-Level Cache and when would you avoid it?**

**Use L2 Cache when:**
- Data is read frequently and updated rarely (e.g. country list, config, product catalog)
- Same data is needed across many user sessions

**Avoid L2 Cache when:**
- Data changes frequently (e.g. stock prices, live inventory)
- Data is user-specific (caching user data in shared cache = security risk)
- Using `@Modifying` bulk queries — they bypass the cache and cause stale reads

> Bulk `UPDATE`/`DELETE` via `@Query` bypasses L2 cache. Always evict or refresh after bulk operations.

---

**Q12. What is the Persistence Context and why does it matter architecturally?**

The Persistence Context is a **first-level cache + identity map** — it guarantees that within one transaction, loading the same entity twice returns the same Java object (not two different copies).

```java
User u1 = repo.findById(1L).get();
User u2 = repo.findById(1L).get();
System.out.println(u1 == u2); // true — same object, same PC
```

Architecturally important because:
- It prevents duplicate UPDATE statements
- It enables dirty checking
- It causes `LazyInitializationException` when session closes before lazy access

---

**Q13. How does `@Transactional` propagation work? When would you use `REQUIRES_NEW`?**

| Propagation | Behavior |
|-------------|----------|
| `REQUIRED` (default) | Join existing transaction, or create new one |
| `REQUIRES_NEW` | Always create a new transaction, suspend the current one |
| `NESTED` | Create a savepoint within existing transaction |
| `SUPPORTS` | Run in transaction if exists, otherwise without |
| `NOT_SUPPORTED` | Always run without transaction |
| `NEVER` | Throw exception if transaction exists |
| `MANDATORY` | Throw exception if NO transaction exists |

**Use `REQUIRES_NEW` when:** you want an operation to commit independently — e.g. audit logging that must persist even if the main transaction rolls back.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void saveAuditLog(AuditLog log) {
    auditRepo.save(log); // commits even if outer tx rolls back
}
```

---

## 🪤 Category 3: Tricky Points

---

**Q14. Does calling `save()` always hit the database?**

**No.** `save()` in Spring Data JPA calls:
- `persist()` for new entities (no ID) → INSERT scheduled at commit
- `merge()` for existing entities → UPDATE scheduled at commit

The actual SQL only fires at **flush time** (usually on commit), not at `save()` call time — unless you call `saveAndFlush()`.

---

**Q15. Can you use `@Transactional` on a private method?**

**No.** Spring's `@Transactional` uses a proxy. The proxy wraps the bean, not individual methods. If you call a `@Transactional` method from within the same class (self-invocation), the transaction is **silently skipped**.

```java
// ❌ This transaction will NOT apply
public void outer() {
    this.inner(); // self-invocation bypasses proxy
}

@Transactional
public void inner() { ... }

// ✅ Fix: inject self, or move inner() to a separate @Service bean
```

---

**Q16. What happens if you forget `mappedBy` on a `@OneToMany`?**

JPA creates a **join table** you didn't ask for, e.g. `user_orders`, in addition to the FK column in the `orders` table. You end up with duplicate relationship storage and data integrity issues.

```java
// ❌ Missing mappedBy — JPA creates a join table!
@OneToMany
private List<Order> orders;

// ✅ Correct
@OneToMany(mappedBy = "user")
private List<Order> orders;
```

---

**Q17. What is the difference between `fetchgraph` and `loadgraph` hints?**

| Hint | Behavior |
|------|----------|
| `fetchgraph` | Only loads fields specified in the graph — everything else is LAZY |
| `loadgraph` | Loads fields in the graph + anything already marked EAGER in the entity |

> `fetchgraph` is stricter and usually preferred for performance control.

---

**Q18. Does `@Modifying` auto-clear the Persistence Context?**

By default, **no**. After a bulk `UPDATE` or `DELETE` via `@Modifying`, the Persistence Context can hold stale entities. You need to clear it manually:

```java
@Modifying(clearAutomatically = true) // ← clears PC after bulk operation
@Transactional
@Query("UPDATE User u SET u.status = 'INACTIVE' WHERE u.lastLogin < :date")
int deactivateOldUsers(@Param("date") LocalDate date);
```

---

**Q19. What is the difference between `@Query(nativeQuery = true)` and regular JPQL?**

| | JPQL | Native Query |
|--|------|-------------|
| Language | Entity/field names | Table/column names |
| DB portable | Yes | No |
| Full SQL power | Limited | Yes (window functions, hints, etc.) |
| Hibernate intercepts | Yes | No (bypasses mapping) |

> Use native queries only when JPQL can't express what you need (e.g. `PARTITION BY`, DB-specific functions).

---

## ⚠️ Category 4: Exceptional / Edge Cases

---

**Q20. What happens when you call `em.merge()` on a TRANSIENT entity (no ID)?**

It behaves like `persist()` — Hibernate inserts it and returns a new managed copy. But the original object passed in remains TRANSIENT. This is a subtle bug:

```java
User detached = new User(); // no ID
User managed = em.merge(detached); // inserts and returns managed copy
// detached is still TRANSIENT — don't use it further
```

---

**Q21. Can `@Version` be a `Long`, `Integer`, or `Timestamp`?**

Yes — all are valid:
```java
@Version private int version;       // integer counter
@Version private Long version;      // long counter
@Version private Timestamp version; // timestamp-based (less precise, avoid)
```

> Prefer `int` or `Long`. Timestamp-based versioning can fail on systems where two updates happen within the same millisecond.

---

**Q22. What happens with `CascadeType.REMOVE` on a `@ManyToMany`?**

It will delete the **associated entities** themselves — not just the join table rows. This is almost always wrong in a many-to-many because the associated entity may be shared by other parents.

```java
// ❌ Dangerous — deletes the Student entity, not just the enrollment
@ManyToMany(cascade = CascadeType.REMOVE)
private List<Student> students;

// ✅ Correct — only removes from join table, not the Student itself
@ManyToMany // no cascade remove
private List<Student> students;
```

---

**Q23. What happens if two entities have a bidirectional relationship and you don't sync both sides?**

Within the same persistence context, the in-memory state becomes inconsistent even if the DB is fine:

```java
// ❌ Only sets one side
department.getEmployees().add(emp);
// emp.setDepartment(department) — missing!

// Now within the same transaction:
emp.getDepartment(); // returns null — PC hasn't been synced
```

> Always write a helper method to sync both sides of a bidirectional relationship.

---

**Q24. `LazyInitializationException` inside `@Transactional` — why does it still happen?**

Even with `@Transactional`, it can happen if:
1. The `@Transactional` is on a different layer and the entity is passed *outside* the transaction boundary
2. You're using Spring's **Open Session in View** is disabled and you access lazy data in the controller/view
3. The entity is serialized (e.g. to JSON by Jackson) after the session closes

> Fix: use `@Transactional(readOnly = true)` on your service, fetch only what you need with JOIN FETCH, or use DTO projections.

---

**Q25. What is `MultipleBagFetchException` and how do you fix it?**

Hibernate throws this when you try to JOIN FETCH **two separate `List` (Bag) collections** at once:

```java
// ❌ Throws MultipleBagFetchException
"SELECT a FROM Author a JOIN FETCH a.books JOIN FETCH a.awards"
```

**Fix 1:** Change one `List` to `Set`:
```java
private Set<Book> books;  // Set avoids the Bag issue
```

**Fix 2:** Use separate queries:
```java
// Query 1: fetch with books
// Query 2: fetch with awards
// Hibernate merges them via L1 cache
```

---

## 🧠 Category 5: Memory / Quick-Recall Points

---

**Q26. What are the defaults you must remember?**

| Item | Default |
|------|---------|
| `@ManyToOne` fetch | `EAGER` ⚠️ |
| `@OneToMany` fetch | `LAZY` ✅ |
| `@OneToOne` fetch | `EAGER` ⚠️ |
| `@ManyToMany` fetch | `LAZY` ✅ |
| `@Transactional` rollback | Only on `RuntimeException` |
| `@Transactional` propagation | `REQUIRED` |
| `save()` on new entity | calls `persist()` |
| `save()` on existing entity | calls `merge()` |
| Inheritance default | `SINGLE_TABLE` |
| `@GeneratedValue` default strategy | `AUTO` |

---

**Q27. What is the difference between `flush()` and `commit()`?**

| | `flush()` | `commit()` |
|--|-----------|------------|
| Sends SQL to DB | Yes | Yes (flush first, then commit) |
| Makes changes permanent | No (still in transaction) | Yes |
| Releases locks | No | Yes |

> `flush()` = SQL sent. `commit()` = SQL permanent. You can flush multiple times in one transaction.

---

**Q28. Lifecycle annotations — when do they fire?**

| Annotation | Fires When |
|------------|------------|
| `@PrePersist` | Before INSERT |
| `@PostPersist` | After INSERT |
| `@PreUpdate` | Before UPDATE |
| `@PostUpdate` | After UPDATE |
| `@PreRemove` | Before DELETE |
| `@PostRemove` | After DELETE |
| `@PostLoad` | After entity is loaded from DB |

Common use: auto-set `createdAt` / `updatedAt`:
```java
@PrePersist
public void onCreate() { this.createdAt = LocalDateTime.now(); }

@PreUpdate
public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
```

---

**Q29. What does `ddl-auto` mean and which value should you use where?**

| Value | Behavior | Use In |
|-------|----------|--------|
| `none` | Do nothing | Production |
| `validate` | Validate schema, throw if mismatch | Production / Staging |
| `update` | Add missing columns/tables | Development |
| `create` | Drop and recreate on startup | Development / Testing |
| `create-drop` | Drop on shutdown too | Testing |

> **Never** use `create` or `update` in production. Use migration tools like **Flyway** or **Liquibase** instead.

---

**Q30. What is the Open Session in View (OSIV) pattern? Is it good or bad?**

OSIV keeps the Hibernate Session open until the HTTP response is fully written — so lazy loading works in the view/controller layer.

Spring Boot enables it **by default** (`spring.jpa.open-in-view=true`).

**Problem:** It silently triggers lazy loads during JSON serialization → unexpected DB queries in the web layer → hard-to-trace N+1.

**Best practice:** Disable it and use service-layer transactions with explicit fetching:
```properties
spring.jpa.open-in-view=false
```

---

## 💡 Category 6: Bonus — What Interviewers Love to Ask

---

**Q31. Why is `equals()` and `hashCode()` important in JPA entities?**

In sets and maps (especially in `@ManyToMany`), Java uses `equals()`/`hashCode()` to compare objects. If you use the default (identity-based), a detached and re-loaded entity will appear as a *different* object — causing duplicates in sets.

```java
// ✅ Use business key or ID-based equals/hashCode
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof User)) return false;
    User u = (User) o;
    return id != null && id.equals(u.id);
}

@Override
public int hashCode() { return getClass().hashCode(); }
```

> Using `getClass().hashCode()` (not `Objects.hash(id)`) keeps the hash stable before and after `persist()`.

---

**Q32. What is the difference between `@OneToOne` with `@MapsId` vs plain `@JoinColumn`?**

`@MapsId` makes the child entity share the **same primary key** as the parent — no separate FK column needed:

```java
@Entity
public class UserProfile {
    @Id
    private Long id; // same value as User.id

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private User user;
}
```

> Use `@MapsId` when the child cannot exist independently and should share the parent's PK. Eliminates an extra auto-generated ID column.

---

**Q33. What is a Projection and when do you use it over an Entity?**

A Projection fetches only specific fields without loading the full entity into the Persistence Context — faster and lighter.

```java
// Interface-based projection
public interface UserSummary {
    String getName();
    String getEmail();
}

List<UserSummary> findAllProjectedBy(); // Spring Data generates this

// Class-based (DTO) projection via JPQL
@Query("SELECT new com.app.UserDTO(u.name, u.email) FROM User u")
List<UserDTO> findAllDTO();
```

> Use projections when you are displaying read-only data and don't need to modify the entity. Always prefer this over loading full entities for list/search views.

---

**Q34. How do you handle soft deletes in JPA?**

Use `@Where` (Hibernate) or `@SQLRestriction` (Hibernate 6+) to automatically filter deleted records:

```java
@Entity
@Where(clause = "deleted = false")  // always applied to queries
public class User {
    private boolean deleted = false;

    @PreRemove
    public void softDelete() {
        this.deleted = true;
    }
}
```

> With `@Where`, normal `findAll()` automatically excludes soft-deleted rows. Hard delete is still possible via native query.

---

**Q35. What is `@BatchSize` and how does it reduce N+1 without JOIN FETCH?**

`@BatchSize` tells Hibernate: "when you need to load lazy associations, load them in batches of N instead of one at a time."

```java
@OneToMany(mappedBy = "author", fetch = FetchType.LAZY)
@BatchSize(size = 20)
private List<Book> books;
```

Instead of N queries (one per author), Hibernate fires `⌈N/20⌉` queries using `WHERE author_id IN (?, ?, ... 20 ids)`.

> Not as clean as JOIN FETCH but useful when JOIN FETCH causes a Cartesian product (e.g. fetching two collections).