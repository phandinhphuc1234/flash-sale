package com.philia.flashsale.authentication.account.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for normalized account identity lookups. */
public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {
    Optional<AccountJpaEntity> findByEmailNormalized(String emailNormalized);
    Optional<AccountJpaEntity> findByUsernameNormalized(String usernameNormalized);
    boolean existsByEmailNormalized(String emailNormalized);
    boolean existsByUsernameNormalized(String usernameNormalized);
}
