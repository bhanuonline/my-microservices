JPA is a Specification, Not an Implementation
What does that mean?
Think of JPA like a rulebook or a contract — it defines what should be done, but not how to do it

JPA works the same way:
JPA = the standard/rulebook
Hibernate, EclipseLink, OpenJPA = manufacturers who implement that rulebook
Your Java code = the device that just uses the standard

package :jakarta.persistence

Hibernate is not JPA
Hibernate existed before JPA. JPA was actually inspired by Hibernate. Hibernate has extra features beyond the JPA spec

Spring Data JPA adds another layer on top
Spring Data JPA     ← makes JPA even easier (repositories, auto queries)
↓
JPA             ← specification
↓
Hibernate         ← default implementation in Spring Boot
↓
Database

spring-boot-starter-data-jpa
├── Spring Data JPA      ← smart repository layer
├── Hibernate            ← JPA implementation (the engine)
└── JPA (jakarta)        ← the specification/contract

What Spring Data JPA Actually Is
With plain JPA + Hibernate, you still have to write this:
// Plain JPA - you write this yourself
@PersistenceContext
EntityManager em;

public User findById(Long id) {
return em.find(User.class, id);
}

public void save(User user) {
em.persist(user);
}

public List<User> findAll() {
return em.createQuery("SELECT u FROM User u", User.class)
.getResultList();
}

With Spring Data JPA, you write this:
// Spring Data JPA - just declare the interface, done!
public interface UserRepository extends JpaRepository<User, Long> {
// save(), findById(), findAll(), delete() etc.
// are ALL auto-generated — you write zero implementation
}

The Magic — Query Methods
Spring Data JPA can even generate queries just from method names:

All 3 Layers Working Together
Your Code (UserRepository.findByEmail())
↓
Spring Data JPA          → generates query, manages repositories
↓
JPA (spec)           → defines EntityManager, @Entity, @Query rules
↓
Hibernate             → translates to real SQL, talks to DB
↓
Database              → MySQL / PostgreSQL / etc.

# Entity Mapping :

mappedBy = "user"
→ "I am NOT the owner of this relationship"
→ "Look at the 'user' field in Order class for the foreign key"
→ Prevents JPA from creating a duplicate join table

CascadeType.ALL
→ If you save a User → orders are saved too
→ If you delete a User → orders are deleted too

FetchType.LAZY
→ Orders are NOT loaded when you load a User
→ Only loaded when you actually call user.getOrders()
→ Better performance ✅

# How to Decide @ManyToOne vs @OneToMany
@ManyToOne   →  "Many [this entity] belong to One [that entity]"
@OneToMany   →  "One [this entity] has Many [that entity]"
both annotations describe the SAME relationship, just from different sides.

"Many" side  →  @ManyToOne + @JoinColumn  (has the FK column in DB)
"One" side   →  @OneToMany + mappedBy     (no FK column, just a reference)
The "Many" side always owns the relationship and gets @JoinColumn
The "One" side always gets mappedBy
ex:
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

# @ManyToMany

OneToMany  → FK column in one table
ManyToMany → needs a SEPARATE join table


# Occor exception while debugging 
_LazyInitializationException_=LAZY + Session Closed  ,could not initialize proxy - no Session
Parent entity is loaded
Session/Transaction closes
Later code tries to access lazy object
solution :
Keep all associations LAZY by default.

Use:
- JOIN FETCH
- @EntityGraph
- DTO Projection
  LazyInitializationException occurs when Hibernate tries to load a LAZY association after the Session/Persistence Context has been closed.
The preferred fix is to fetch the required association using JOIN FETCH or EntityGraph instead of changing fetch type to EAGER.

_JdbcTypeRecommendationException_ occurs when Hibernate encounters a custom Java object (Entity) and cannot determine how to map it to a database column.
Fix:
Use proper relationship annotations such as
@ManyToOne, @OneToOne, @OneToMany, or @ManyToMany
instead of @Column.

# Embeddables: @Embeddable, @Embedded

Use it when:
✅ Fields logically belong together (street, city, state)
✅ You want to reuse the group across multiple entities
✅ You don't need to query the group independently
✅ No need for a separate table

❌ Don't use when you need to search/filter by it independently
❌ Don't use when it needs its own lifecycle (create/delete separately)

@Embeddable  →  "I am just a group of fields, I have no table",a reusable group of fields
@Embedded    →  "Take that group and put it inside my table"
Two Java classes — but still ONE table in database.
That is all @Embeddable does.

Why Not Just Put Fields Directly in User?
You can — but @Embeddable gives you:
1. Clean code
2. Reuse the same group in multiple places

# EntityManager & Lifecycle
What is EntityManager?
Think of EntityManager as a middleman between your Java code and the database.
Your Java Code
↓
EntityManager    ← you talk to this
↓
Hibernate
↓
Database

Entity Lifecycle — 4 States
new User()
↓
TRANSIENT
↓  em.persist()
MANAGED  ←─────────────── em.merge()
↓                          ↑
(commit)              DETACHED ─┘
↓            (session closed /
REMOVED             em.detach())
em.remove()

All 4 States Together

new User()
│
│  Just a Java object
│  DB doesn't know
▼
TRANSIENT
│
│  entityManager.persist(user)
│  or userRepository.save(user)
▼
MANAGED ◄──────────────────────────────┐
│                                  │
│  Auto-tracks changes             │ entityManager.merge(user)
│  Dirty checking active           │
│                                  │
├──── session closes ──────────► DETACHED
│     or detach()                  │
│                                  │
│  entityManager.remove(user)      │
▼                                  │
REMOVED                                │
│                                  │
│  on commit                       │
▼                                  │
DELETE fired                           │
│                                  │
└── object becomes TRANSIENT ──────┘

Dirty Checking — The Magic of MANAGED state
@Transactional
public void updateName(Long id, String newName) {
User user = userRepository.findById(id).get();
// user is now MANAGED

    user.setName(newName);
    // ↑ No save() call needed!
    // JPA sees the change automatically
    // UPDATE fires when @Transactional commits

}
// ← transaction commits here → UPDATE users SET name=... fires!
// You might be tempted to do this — not needed!
user.setName(newName);
userRepository.save(user); // redundant inside @Transactional

save() in Spring Data JPA is smart — it calls persist() for new objects and merge() for existing ones automatically.

TRANSIENT  → "I am just a Java object, DB has no idea I exist"
MANAGED    → "JPA is watching me, every change I make gets saved"
DETACHED   → "JPA stopped watching me, changes are ignored"
REMOVED    → "JPA will delete me from DB on next commit"

JPA specification defines:
✅ EntityManager        (interface)
✅ Entity lifecycle     (transient, managed, detached, removed)
✅ @Entity, @Id, @Table (annotations)
✅ JPQL                 (query language)
✅ @OneToMany, etc.     (relationship annotations)

JPA says:
"You MUST have an EntityManager"
"It MUST have persist(), merge(), remove(), find()"
"It MUST follow the 4 lifecycle states"

Hibernate says:
"OK, here is our implementation of all that"

# What is @PersistenceContext?
@PersistenceContext is just a way to inject EntityManager into your class.
Like how @Autowired injects a Spring bean — @PersistenceContext injects an EntityManager.

Why Not Just Use @Autowired for EntityManager?
Because EntityManager is not a normal Spring bean.

Normal Spring Bean          EntityManager
------------------          -------------
One instance shared         New instance per
across whole app            transaction
(singleton)                 (not singleton)

@PersistenceContext tells Spring:
"Give me a new EntityManager for each transaction, not a shared one."

@PersistenceContext vs @Autowired

1. Purpose
   @PersistenceContext -> Used for EntityManager only
   @Autowired          -> Used for any Spring bean

2. Instance Management
   @PersistenceContext -> Provides transaction-scoped EntityManager
   @Autowired          -> Injects the actual Spring bean instance

3. Lifecycle
   @PersistenceContext -> Managed by JPA/Hibernate
   @Autowired          -> Managed by Spring Container

4. Specification
   @PersistenceContext -> JPA Specification
   @Autowired          -> Spring Framework

5. Thread Safety
   @PersistenceContext -> Thread-safe (through proxy)
   @Autowired          -> Depends on the injected bean

6. Recommended Usage
   @PersistenceContext -> For EntityManager injection
   @Autowired          -> For Services, Repositories, Components, etc.

Example:

@PersistenceContext
private EntityManager entityManager;

@Autowired
private UserService userService;

Do You Need This in Real Projects?
Mostly NO — because Spring Data JPA handles it for you

When Would You Use It Directly?
Only when you need custom complex queries that Spring Data JPA can't handle:

# JPQL (JPA Query Language)
What is JPQL?
JPQL is a query language just like SQL — but instead of querying tables and columns, you query Java classes and fields.

How to Write JPQL in Spring Data JPA
Use @Query annotation on your repository

Syntax
// SQL pattern
SELECT * FROM table WHERE condition

// JPQL pattern
SELECT u FROM User u WHERE condition
//     ↑         ↑
//   alias    entity name (Java class)

@Query("SELECT u FROM User u WHERE u.name = :name")
@Query("SELECT u.name, u.email FROM User u")
@Query("SELECT u FROM User u WHERE u.age > :age AND u.name = :name")
@Query("SELECT u FROM User u ORDER BY u.age DESC")
@Query("SELECT u FROM User u WHERE u.name LIKE %:keyword%")
@Query("SELECT COUNT(u) FROM User u WHERE u.age > :age")
@Query("SELECT o FROM Order o WHERE o.user.id = :userId")
@Query("SELECT u FROM User u JOIN FETCH u.orders WHERE u.id = :id") : join in sql
@Query("SELECT u FROM User u WHERE u.name = :name AND u.age = :age")

@Modifying
@Transactional
@Query("UPDATE User u SET u.name = :name WHERE u.id = :id")

@Modifying
@Transactional
@Query("DELETE FROM User u WHERE u.age < :age")

@Modifying — required for UPDATE and DELETE queries
Returns int — number of rows affected

# JPA Side — You Use Annotations
// Entity mapping
@Entity
@Table
@Id
@GeneratedValue
@Column
@Transient
@Embeddable
@Embedded

// Relationships
@OneToOne
@OneToMany
@ManyToOne
@ManyToMany
@JoinColumn
@JoinTable

// Lifecycle hooks
@PrePersist
@PostPersist
@PreUpdate
@PostUpdate
@PreRemove
@PostRemove

// Querying
JPQL                    ← JPA's query language
@NamedQuery             ← define query on entity class
@PersistenceContext     ← inject EntityManager

Spring Data JPA Side — You Use Repositories
These come from org.springframework.data package:

// Repository interfaces
JpaRepository        ← most common, use this
CrudRepository       ← basic CRUD only
PagingAndSortingRepository ← adds pagination

// Annotations
@Repository          ← mark class as repository
@Query               ← write JPQL/SQL queries
@Modifying           ← for UPDATE/DELETE queries
@Transactional       ← manage transactions
@Param               ← name query parameters
@EnableJpaRepositories ← enable JPA in Spring Boot

Hibernate Side — You Use Configuration Only
You rarely touch Hibernate directly. You just configure it in application.properties:

properties# Hibernate configuration
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true


# Pagination, ordering, aggregation