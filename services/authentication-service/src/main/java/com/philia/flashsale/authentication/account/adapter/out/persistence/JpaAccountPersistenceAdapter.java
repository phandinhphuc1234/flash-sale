package com.philia.flashsale.authentication.account.adapter.out.persistence;

import java.util.Optional;

import com.philia.flashsale.authentication.account.application.registration.RegisterAccountPort;
import com.philia.flashsale.authentication.account.application.bootstrap.AdminBootstrapAccountPort;
import com.philia.flashsale.authentication.account.domain.Account;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "runtime-enabled", havingValue = "true", matchIfMissing = true)
/** Implements registration persistence while keeping repository details outside application code. */
public class JpaAccountPersistenceAdapter implements RegisterAccountPort, AdminBootstrapAccountPort {
    private final AccountJpaRepository repository;

    public JpaAccountPersistenceAdapter(AccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Account> findByEmailNormalized(String emailNormalized) {
        return repository.findByEmailNormalized(emailNormalized).map(AccountPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Account> findByUsernameNormalized(String usernameNormalized) {
        return repository.findByUsernameNormalized(usernameNormalized).map(AccountPersistenceMapper::toDomain);
    }

    @Override
    public Account save(Account account) {
        try {
            return AccountPersistenceMapper.toDomain(repository.saveAndFlush(AccountPersistenceMapper.toEntity(account)));
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        }
    }
}
