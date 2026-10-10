package com.example.common.idempotency;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Consumer-side idempotency check. Call {@link #claim(UUID, String)} at the top
 * of every message handler — returns true the first time (eventId, consumer) is
 * seen, false on every replay.
 *
 * Why not JpaRepository.save()?
 *   Hibernate treats a new entity with a client-assigned @Id as "possibly detached",
 *   SELECTs first, and silently converts the second call into an UPDATE — so
 *   no DataIntegrityViolationException ever fires.
 *
 * Why TransactionTemplate (not @Transactional REQUIRES_NEW)?
 *   The duplicate-key SQLException translates to DataIntegrityViolationException
 *   on flush, but under @Transactional the commit happens AFTER the method
 *   returns, so a method-level catch never sees it. TransactionTemplate commits
 *   inside the executeWithoutResult callback where catch does see it.
 *
 * Why @Qualifier?
 *   Services with spring.kafka.producer.transaction-id-prefix enabled also have
 *   a kafkaTransactionManager bean. Unqualified injection would be ambiguous.
 *   The dedup write is JPA, so pin to "transactionManager" (Spring Boot's default
 *   name for the JPA tx manager from HibernateJpaConfiguration).
 */
@Component
public class IdempotencyGuard {

    @PersistenceContext
    private EntityManager entityManager;

    private final TransactionTemplate txTemplate;

    public IdempotencyGuard(@Qualifier("transactionManager") PlatformTransactionManager txManager) {
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * @return true if the (eventId, consumer) pair is new; false if already seen.
     */
    public boolean claim(UUID eventId, String consumer) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null — producer forgot to set it");
        }
        UUID key = compositeKey(eventId, consumer);
        try {
            txTemplate.executeWithoutResult(status -> {
                entityManager.persist(new ProcessedEvent(key, consumer));
                entityManager.flush();
            });
            return true;
        } catch (DataIntegrityViolationException dup) {
            return false;
        }
    }

    private static UUID compositeKey(UUID eventId, String consumer) {
        return UUID.nameUUIDFromBytes((eventId + "|" + consumer).getBytes());
    }
}
