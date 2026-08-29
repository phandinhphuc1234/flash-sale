package com.philia.flashsale.cart.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.cart.application.port.in.GetCartUseCase;
import com.philia.flashsale.cart.application.port.in.ClearCartUseCase;
import com.philia.flashsale.cart.application.query.GetCartQuery;
import com.philia.flashsale.cart.application.result.CartItemResult;
import com.philia.flashsale.cart.application.result.CartResult;
import com.philia.flashsale.cart.application.port.in.RemoveCartItemUseCase;
import com.philia.flashsale.cart.application.port.in.SetCartItemUseCase;
import com.philia.flashsale.cart.websupport.error.CartHttpExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GetCartHttpTests {
    private final SetCartItemUseCase setCartItem = mock(SetCartItemUseCase.class);
    private final RemoveCartItemUseCase removeCartItem = mock(RemoveCartItemUseCase.class);
    private final ClearCartUseCase clearCart = mock(ClearCartUseCase.class);
    private final GetCartUseCase getCart = mock(GetCartUseCase.class);
    private MockMvc mvc;
    private JwtAuthenticationToken shopper;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CartController(setCartItem, removeCartItem,
                        clearCart, getCart, new CartWebMapper()))
                .setControllerAdvice(new CartHttpExceptionHandler())
                .build();
        UUID owner = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("shopper-token").header("typ", "at+jwt")
                .subject(owner.toString()).build();
        shopper = new JwtAuthenticationToken(jwt, List.of());
    }

    @Test
    void getReturnsCurrentDetailsWithNoStoreAndTraceHeader() throws Exception {
        UUID variant = UUID.randomUUID();
        CartItemResult item = new CartItemResult(variant, 2, true, true, null,
                UUID.randomUUID(), "slug", "Product", "Variant", "SKU", null, "VND", null,
                Instant.parse("2026-08-29T00:00:00Z"));
        when(getCart.get(any())).thenReturn(new CartResult(List.of(item), 1, 2,
                Instant.parse("2026-08-29T00:00:00Z")));

        mvc.perform(get("/api/v1/cart").principal(shopper).header("X-Trace-Id", "trace-read"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Trace-Id", "trace-read"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.distinctItemCount").value(1))
                .andExpect(jsonPath("$.data.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.items[0].variantId").value(variant.toString()))
                .andExpect(jsonPath("$.data.items[0].productName").value("Product"));
    }

    @Test
    void absentCartIsSuccessfulEmptyCart() throws Exception {
        when(getCart.get(any())).thenReturn(CartResult.empty());

        mvc.perform(get("/api/v1/cart").principal(shopper))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.distinctItemCount").value(0))
                .andExpect(jsonPath("$.data.totalQuantity").value(0))
                .andExpect(jsonPath("$.data.updatedAt").doesNotExist());
        verify(getCart).get(any(GetCartQuery.class));
    }

    @Test
    void missingPrincipalIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/cart").header("X-Trace-Id", "trace-anonymous"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Trace-Id", "trace-anonymous"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }
}
