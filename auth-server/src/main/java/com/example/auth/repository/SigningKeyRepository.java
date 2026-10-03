package com.example.auth.repository;

import com.example.auth.entity.SigningKeyEntity;
import com.example.auth.entity.SigningKeyEntity.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SigningKeyRepository extends JpaRepository<SigningKeyEntity, String> {

    /** First active key — kept for bootstrap fallback if no PRIMARY exists yet. */
    Optional<SigningKeyEntity> findFirstByActiveTrue();

    /** Single PRIMARY key — the one that signs new tokens. */
    Optional<SigningKeyEntity> findFirstByStatus(Status status);

    /** All active keys (PRIMARY + SECONDARY) — used to build the JWKS response. */
    List<SigningKeyEntity> findAllByActiveTrueOrderByCreatedAtDesc();

    /** Newest first, all statuses — used by the admin list view. */
    List<SigningKeyEntity> findAllByOrderByCreatedAtDesc();
}
