package com.philia.flashsale.authentication.account.application.profile;

import java.util.List;
import java.util.UUID;

import com.philia.flashsale.authentication.account.domain.Account;

/** Safe read model exposed to the authenticated account owner. */
public record AccountProfile(UUID id, String login, String username, String email, String displayName,
                             String fullName, String phone, String address, String status, List<String> authorities) {
    /** Compatibility constructor for existing application/web contract tests. */
    public AccountProfile(UUID id, String login, String username, String email, String displayName,
                          String status, List<String> authorities) {
        this(id, login, username, email, displayName, null, null, null, status, authorities);
    }

    public AccountProfile {
        authorities = List.copyOf(authorities == null ? List.of() : authorities);
    }

    public static AccountProfile from(Account account) {
        String username = account.username() == null || account.username().isBlank()
                ? null : account.username();
        String login = username == null ? account.email() : username;
        String fullName = account.fullName() == null || account.fullName().isBlank() ? null : account.fullName();
        String displayName = fullName != null ? fullName : (username == null ? account.email() : username);
        return new AccountProfile(account.id(), login, username, account.email(), displayName,
                fullName, account.phone(), account.address(), account.status().name(), account.role().authorities());
    }
}
