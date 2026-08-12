package com.philia.flashsale.authentication.account.domain;

import java.util.List;

/** Authorities that can be assigned by the Authentication service, never by public registration. */
public enum AccountRole {
    ROLE_USER,
    ROLE_ADMIN;

    public List<String> authorities() {
        return this == ROLE_ADMIN
                ? List.of("ROLE_ADMIN", "CATALOG_ADMIN", "INVENTORY_ADMIN", "CAMPAIGN_ADMIN")
                : List.of("ROLE_USER");
    }
}
