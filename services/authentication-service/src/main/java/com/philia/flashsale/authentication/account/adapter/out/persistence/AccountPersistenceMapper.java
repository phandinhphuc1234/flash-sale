package com.philia.flashsale.authentication.account.adapter.out.persistence;

import com.philia.flashsale.authentication.account.domain.Account;

/** Boundary mapper that keeps JPA entities separate from the Account aggregate. */
public final class AccountPersistenceMapper {
    private AccountPersistenceMapper() { }

    public static Account toDomain(AccountJpaEntity entity) {
        return Account.restore(entity.id(), entity.email(), entity.emailNormalized(), entity.username(),
                entity.usernameNormalized(), entity.passwordHash(), entity.role(), entity.status(),
                entity.lockedUntil(), entity.lastLoginAt(), entity.createdAt(), entity.updatedAt());
    }

    public static AccountJpaEntity toEntity(Account account) {
        return new AccountJpaEntity(account.id(), account.email(), account.emailNormalized(), account.username(),
                account.usernameNormalized(), account.passwordHash(), account.role(), account.status(),
                account.lockedUntil(), account.lastLoginAt(), account.createdAt(), account.updatedAt());
    }
}
