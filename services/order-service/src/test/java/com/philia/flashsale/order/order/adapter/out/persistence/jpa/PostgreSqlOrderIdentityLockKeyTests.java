package com.philia.flashsale.order.order.adapter.out.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgreSqlOrderIdentityLockKeyTests {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void sameIdentityAndNamespaceAlwaysProduceTheSameKey() {
        assertThat(PostgreSqlOrderIdentityLockKey.forPurchaseRequest(ID))
                .isEqualTo(PostgreSqlOrderIdentityLockKey.forPurchaseRequest(ID));
    }

    @Test
    void identityAndNamespaceChangesProduceDifferentLockDomains() {
        assertThat(PostgreSqlOrderIdentityLockKey.forPurchaseRequest(ID))
                .isNotEqualTo(PostgreSqlOrderIdentityLockKey.forPurchaseRequest(UUID.randomUUID()));
        assertThat(PostgreSqlOrderIdentityLockKey.forPurchaseRequest(ID))
                .isNotEqualTo(PostgreSqlOrderIdentityLockKey.forReservation(ID));
    }

    @Test
    void nullAndBlankInputsAreRejected() {
        assertThatThrownBy(() -> PostgreSqlOrderIdentityLockKey.from(" ", ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PostgreSqlOrderIdentityLockKey.forPurchaseRequest(null))
                .isInstanceOf(NullPointerException.class);
    }
}
