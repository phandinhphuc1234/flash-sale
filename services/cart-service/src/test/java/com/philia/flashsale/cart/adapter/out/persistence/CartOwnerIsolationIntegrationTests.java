package com.philia.flashsale.cart.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.cart.adapter.out.persistence.jpa.CartPersistenceAdapter;
import com.philia.flashsale.cart.adapter.out.persistence.jpa.repository.CartItemJpaRepository;
import com.philia.flashsale.cart.adapter.out.persistence.jpa.repository.CartJpaRepository;
import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Instant;
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
@Import(CartPersistenceAdapter.class)
@Testcontainers(disabledWithoutDocker = true)
class CartOwnerIsolationIntegrationTests {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("cart_db").withUsername("flashsale").withPassword("test");

    @Autowired
    private CartPersistenceAdapter adapter;
    @Autowired
    private CartJpaRepository carts;
    @Autowired
    private CartItemJpaRepository items;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void clearDeletesOnlyTheAuthenticatedOwnersItemsAndKeepsOtherCart() {
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        UUID firstVariant = UUID.randomUUID();
        UUID secondVariant = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-29T00:00:00Z");
        adapter.upsertItem(firstOwner, firstVariant, CartQuantity.of(1), now);
        adapter.upsertItem(secondOwner, secondVariant, CartQuantity.of(2), now);

        adapter.clearItems(firstOwner, now.plusSeconds(1));

        UUID firstCartId = carts.findByOwnerId(firstOwner).orElseThrow().getId();
        UUID secondCartId = carts.findByOwnerId(secondOwner).orElseThrow().getId();
        assertThat(items.findByCartIdOrderByUpdatedAtDescVariantIdAsc(firstCartId)).isEmpty();
        assertThat(items.findByCartIdOrderByUpdatedAtDescVariantIdAsc(secondCartId))
                .extracting(item -> item.getVariantId()).containsExactly(secondVariant);

        adapter.clearItems(UUID.randomUUID(), now.plusSeconds(2));
        assertThat(carts.count()).isEqualTo(2);
        assertThat(items.count()).isEqualTo(1);
    }
}
