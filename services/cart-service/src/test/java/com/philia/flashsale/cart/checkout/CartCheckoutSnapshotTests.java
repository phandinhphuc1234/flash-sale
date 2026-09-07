package com.philia.flashsale.cart.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.cart.adapter.in.web.CartCheckoutSnapshotController;
import com.philia.flashsale.cart.adapter.in.web.CartCheckoutSnapshotMapper;
import com.philia.flashsale.cart.application.exception.CartEmptyException;
import com.philia.flashsale.cart.application.exception.CartNotFoundException;
import com.philia.flashsale.cart.application.port.out.LoadCartCheckoutSnapshotPort;
import com.philia.flashsale.cart.application.query.GetCartCheckoutSnapshotQuery;
import com.philia.flashsale.cart.application.result.CartCheckoutSnapshotItem;
import com.philia.flashsale.cart.application.result.CartCheckoutSnapshotResult;
import com.philia.flashsale.cart.application.usecase.GetCartCheckoutSnapshotService;
import com.philia.flashsale.cart.websupport.error.CartHttpExceptionHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CartCheckoutSnapshotTests {
    private static final Instant CAPTURED_AT = Instant.parse("2026-09-05T01:02:03Z");

    @Test
    void capturesOwnedCartWithoutProductEnrichment() {
        UUID shopper = UUID.randomUUID();
        UUID cart = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        LoadCartCheckoutSnapshotPort port = mock(LoadCartCheckoutSnapshotPort.class);
        when(port.loadCheckoutSnapshot(shopper)).thenReturn(Optional.of(new CartCheckoutSnapshotResult(cart, shopper,
                7, Instant.EPOCH, List.of(new CartCheckoutSnapshotItem(variant, 2, 6)))));

        var result = new GetCartCheckoutSnapshotService(port,
                Clock.fixed(CAPTURED_AT, ZoneOffset.UTC)).get(new GetCartCheckoutSnapshotQuery(shopper));

        assertThat(result.cartId()).isEqualTo(cart);
        assertThat(result.ownerId()).isEqualTo(shopper);
        assertThat(result.cartVersion()).isEqualTo(7);
        assertThat(result.capturedAt()).isEqualTo(CAPTURED_AT);
        assertThat(result.items()).containsExactly(new CartCheckoutSnapshotItem(variant, 2, 6));
    }

    @Test
    void absentAndEmptyCartAreDifferentBusinessOutcomes() {
        UUID shopper = UUID.randomUUID();
        LoadCartCheckoutSnapshotPort port = mock(LoadCartCheckoutSnapshotPort.class);
        var service = new GetCartCheckoutSnapshotService(port, Clock.systemUTC());
        when(port.loadCheckoutSnapshot(shopper)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(new GetCartCheckoutSnapshotQuery(shopper)))
                .isInstanceOf(CartNotFoundException.class);

        when(port.loadCheckoutSnapshot(shopper)).thenReturn(Optional.of(new CartCheckoutSnapshotResult(
                UUID.randomUUID(), shopper, 3, Instant.EPOCH, List.of())));
        assertThatThrownBy(() -> service.get(new GetCartCheckoutSnapshotQuery(shopper)))
                .isInstanceOf(CartEmptyException.class);
    }

    @Test
    void internalHttpResponseContainsOnlyCartIntent() throws Exception {
        UUID shopper = UUID.randomUUID();
        UUID cart = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        var useCase = mock(com.philia.flashsale.cart.application.port.in.GetCartCheckoutSnapshotUseCase.class);
        when(useCase.get(new GetCartCheckoutSnapshotQuery(shopper))).thenReturn(new CartCheckoutSnapshotResult(
                cart, shopper, 4, CAPTURED_AT, List.of(new CartCheckoutSnapshotItem(variant, 1, 4))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CartCheckoutSnapshotController(useCase,
                        new CartCheckoutSnapshotMapper()))
                .setControllerAdvice(new CartHttpExceptionHandler())
                .build();

        mvc.perform(post("/internal/v1/cart-checkout-snapshots")
                        .contentType("application/json")
                        .content("{\"shopperId\":\"" + shopper + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cartId").value(cart.toString()))
                .andExpect(jsonPath("$.data.ownerId").value(shopper.toString()))
                .andExpect(jsonPath("$.data.cartVersion").value(4))
                .andExpect(jsonPath("$.data.items[0].variantId").value(variant.toString()))
                .andExpect(jsonPath("$.data.items[0].itemVersion").value(4))
                .andExpect(jsonPath("$.data.items[0].expectedUnitPrice").doesNotExist());
    }
}
