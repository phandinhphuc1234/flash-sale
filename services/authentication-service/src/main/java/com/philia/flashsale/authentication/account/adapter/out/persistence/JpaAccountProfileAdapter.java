package com.philia.flashsale.authentication.account.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import com.philia.flashsale.authentication.account.application.profile.LoadAccountProfilePort;
import com.philia.flashsale.authentication.account.application.profile.UpdateAccountProfilePort;
import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountFailure;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Persistence adapter for the Authentication-owned profile query. */
@Component
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "runtime-enabled", havingValue = "true", matchIfMissing = true)
public class JpaAccountProfileAdapter implements LoadAccountProfilePort, UpdateAccountProfilePort {
    private final AccountJpaRepository repository;

    public JpaAccountProfileAdapter(AccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        return repository.findById(accountId).map(AccountPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Account> findByUsernameNormalized(String usernameNormalized) {
        return repository.findByUsernameNormalized(usernameNormalized).map(AccountPersistenceMapper::toDomain);
    }

    @Override
    public Account save(Account account) {
        try {
            return AccountPersistenceMapper.toDomain(
                    repository.saveAndFlush(AccountPersistenceMapper.toEntity(account)));
        } catch (DataIntegrityViolationException exception) {
            throw new AccountFailure("AUTH_ACCOUNT_ALREADY_EXISTS", "Username is already in use");
        }
    }
}
