The Core Problem JPA Solves
Writing raw SQL in Java is tedious and error-prone — you manually map every ResultSet row to an object, 
handle connections, write repetitive CRUD queries, and your code becomes tightly coupled to your database schema. 
JPA eliminates most of that boilerplate.
Database independence
Spring Boot uses it everywhere
Handles complex relationships naturally

Core Concepts to Learn (in order)
1. Fundamentals

What JPA is (a specification, not an implementation)
JPA vs Hibernate vs Spring Data JPA — understand the layering
Setting up a project (dependencies: hibernate-core, jakarta.persistence)

2. Entity Mapping

@Entity, @Table, @Id, @GeneratedValue
Column mappings: @Column, @Transient, @Enumerated, @Temporal
Embeddables: @Embeddable, @Embedded

3. Relationships

@OneToOne, @OneToMany, @ManyToOne, @ManyToMany
mappedBy, @JoinColumn, @JoinTable
Fetch types: LAZY vs EAGER (very important!)
Cascading: CascadeType

4. EntityManager & Lifecycle

persist(), merge(), remove(), find()
Entity states: Transient → Managed → Detached → Removed
@PersistenceContext

5. JPQL (JPA Query Language)

Basic SELECT, WHERE, JOIN queries
Named queries: @NamedQuery
Pagination, ordering, aggregation

6. Criteria API

Type-safe programmatic queries
CriteriaBuilder, CriteriaQuery, Root

7. Transactions

@Transactional behavior
Transaction propagation and rollback rules

8. Advanced Topics

N+1 problem and how to fix it (JOIN FETCH, entity graphs)
Caching: First-level (session) vs Second-level cache
Optimistic vs Pessimistic locking (@Version)
Inheritance mapping strategies (SINGLE_TABLE, JOINED, TABLE_PER_CLASS)

