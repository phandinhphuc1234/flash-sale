package com.philia.flashsale.authentication.account.domain;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AccountRoleTest {
    @Test
    void adminRoleIncludesInventoryAuthority() {
        assertTrue(AccountRole.ROLE_ADMIN.authorities().contains("INVENTORY_ADMIN"));
    }
}
