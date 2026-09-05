package com.philia.flashsale.cart.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.cart.adapter.out.persistence.jpa.CartPersistenceAdapter;
import com.philia.flashsale.cart.adapter.out.persistence.jpa.CartReconciliationPersistenceAdapter;
import com.philia.flashsale.cart.application.command.ReconcilePurchasedCartSnapshotCommand;
import com.philia.flashsale.cart.application.result.ReconcilePurchasedCartSnapshotResult;
import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.yaml",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import({CartPersistenceAdapter.class, CartReconciliationPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
class CartReconciliationPersistenceIntegrationTests {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("cart_db").withUsername("flashsale").withPassword("test");

    @Autowired
    private CartPersistenceAdapter carts;
    @Autowired
    private CartReconciliationPersistenceAdapter reconciliation;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void matchingSnapshotIsAppliedAndExactReplayIsIdempotent() {
        UUID owner = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        carts.upsertItem(owner, variant, CartQuantity.of(2), Instant.parse("2026-09-05T00:00:00Z"));
        var snapshot = carts.loadCheckoutSnapshot(owner).orElseThrow();
        var command = command(UUID.randomUUID(), UUID.randomUUID(), owner, snapshot.cartId(),
                snapshot.cartVersion(), List.of(new ReconcilePurchasedCartSnapshotCommand.Item(
                        variant, 2, snapshot.items().getFirst().itemVersion())));

        assertThat(reconciliation.apply(command).outcome())
                .isEqualTo(ReconcilePurchasedCartSnapshotResult.Outcome.APPLIED);
        assertThat(reconciliation.apply(command).outcome())
                .isEqualTo(ReconcilePurchasedCartSnapshotResult.Outcome.REPLAYED);
        assertThat(carts.loadCheckoutSnapshot(owner).orElseThrow().items()).isEmpty();
    }

    @Test
    void changedCartLineProducesPartialNoopWithoutDeletingNewIntent() {
        UUID owner = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        UUID newerVariant = UUID.randomUUID();
        carts.upsertItem(owner, variant, CartQuantity.of(2), Instant.parse("2026-09-05T00:00:00Z"));
        var snapshot = carts.loadCheckoutSnapshot(owner).orElseThrow();
        carts.upsertItem(owner, newerVariant, CartQuantity.of(1), Instant.parse("2026-09-05T00:00:01Z"));
        var command = command(UUID.randomUUID(), UUID.randomUUID(), owner, snapshot.cartId(),
                snapshot.cartVersion(), List.of(
                        new ReconcilePurchasedCartSnapshotCommand.Item(variant, 2,
                                snapshot.items().getFirst().itemVersion()),
                        new ReconcilePurchasedCartSnapshotCommand.Item(newerVariant, 1, 999)));

        var result = reconciliation.apply(command);

        assertThat(result.outcome()).isEqualTo(ReconcilePurchasedCartSnapshotResult.Outcome.PARTIAL_NOOP);
        assertThat(result.removedItemCount()).isEqualTo(1);
        assertThat(carts.loadCheckoutSnapshot(owner).orElseThrow().items())
                .extracting(item -> item.variantId()).containsExactly(newerVariant);
    }

    @Test
    void quantityEditedAfterCheckoutIsPreservedByConditionalCleanup() {
        UUID owner = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        carts.upsertItem(owner, variant, CartQuantity.of(1), Instant.parse("2026-09-05T00:00:00Z"));
        var snapshot = carts.loadCheckoutSnapshot(owner).orElseThrow();

        carts.upsertItem(owner, variant, CartQuantity.of(3), Instant.parse("2026-09-05T00:00:01Z"));
        var result = reconciliation.apply(command(UUID.randomUUID(), UUID.randomUUID(), owner, snapshot.cartId(),
                snapshot.cartVersion(), List.of(new ReconcilePurchasedCartSnapshotCommand.Item(
                        variant, 1, snapshot.items().getFirst().itemVersion()))));

        assertThat(result.outcome()).isEqualTo(ReconcilePurchasedCartSnapshotResult.Outcome.PARTIAL_NOOP);
        assertThat(carts.loadCheckoutSnapshot(owner).orElseThrow().items())
                .singleElement().satisfies(item -> {
                    assertThat(item.variantId()).isEqualTo(variant);
                    assertThat(item.quantity()).isEqualTo(3);
                });
    }

    @Test
    void removeAndReAddSameVariantIsPreservedByItemRevision() {
        UUID owner = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        carts.upsertItem(owner, variant, CartQuantity.of(1), Instant.parse("2026-09-05T00:00:00Z"));
        var snapshot = carts.loadCheckoutSnapshot(owner).orElseThrow();
        carts.removeItem(owner, variant, Instant.parse("2026-09-05T00:00:01Z"));
        carts.upsertItem(owner, variant, CartQuantity.of(2), Instant.parse("2026-09-05T00:00:01Z"));

        var result = reconciliation.apply(command(UUID.randomUUID(), UUID.randomUUID(), owner, snapshot.cartId(),
                snapshot.cartVersion(), List.of(new ReconcilePurchasedCartSnapshotCommand.Item(
                        variant, 1, snapshot.items().getFirst().itemVersion()))));

        assertThat(result.outcome()).isEqualTo(ReconcilePurchasedCartSnapshotResult.Outcome.PARTIAL_NOOP);
        assertThat(carts.loadCheckoutSnapshot(owner).orElseThrow().items())
                .extracting(item -> item.variantId()).containsExactly(variant);
        assertThat(carts.loadCheckoutSnapshot(owner).orElseThrow().items().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void sameOrderWithDifferentPayloadIsRejectedAsConflict() {
        UUID owner = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        carts.upsertItem(owner, variant, CartQuantity.of(1), Instant.parse("2026-09-05T00:00:00Z"));
        var snapshot = carts.loadCheckoutSnapshot(owner).orElseThrow();
        UUID commandId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        var original = command(commandId, orderId, owner, snapshot.cartId(), snapshot.cartVersion(),
                List.of(new ReconcilePurchasedCartSnapshotCommand.Item(
                        variant, 1, snapshot.items().getFirst().itemVersion())));
        var changed = command(commandId, orderId, owner, snapshot.cartId(), snapshot.cartVersion(),
                List.of(new ReconcilePurchasedCartSnapshotCommand.Item(
                        variant, 2, snapshot.items().getFirst().itemVersion())));

        assertThat(reconciliation.apply(original).outcome())
                .isEqualTo(ReconcilePurchasedCartSnapshotResult.Outcome.APPLIED);
        assertThat(reconciliation.apply(changed).outcome())
                .isEqualTo(ReconcilePurchasedCartSnapshotResult.Outcome.CONFLICT);
    }

    @Test
    void wrongOwnerCannotReconcileAnotherCart() {
        UUID owner = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        carts.upsertItem(owner, variant, CartQuantity.of(1), Instant.parse("2026-09-05T00:00:00Z"));
        var snapshot = carts.loadCheckoutSnapshot(owner).orElseThrow();
        var command = command(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), snapshot.cartId(),
                snapshot.cartVersion(), List.of(new ReconcilePurchasedCartSnapshotCommand.Item(
                        variant, 1, snapshot.items().getFirst().itemVersion())));

        assertThatThrownBy(() -> reconciliation.apply(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner");
    }

    private ReconcilePurchasedCartSnapshotCommand command(UUID commandId, UUID orderId, UUID owner, UUID cartId,
            long cartVersion, List<ReconcilePurchasedCartSnapshotCommand.Item> items) {
        return new ReconcilePurchasedCartSnapshotCommand(commandId, orderId, UUID.randomUUID(), cartId, owner,
                cartVersion, Instant.parse("2026-09-05T00:01:00Z"), items,
                "flashsale.cart.checkout.commands.v1", 0, 1, null, null);
    }
}
