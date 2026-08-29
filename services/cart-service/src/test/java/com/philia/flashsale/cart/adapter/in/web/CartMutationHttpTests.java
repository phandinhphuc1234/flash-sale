package com.philia.flashsale.cart.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.cart.application.exception.CartVariantNotFoundException;
import com.philia.flashsale.cart.application.exception.CartVariantNotSellableException;
import com.philia.flashsale.cart.application.exception.ProductDisplayDependencyException;
import com.philia.flashsale.cart.application.port.in.GetCartUseCase;
import com.philia.flashsale.cart.application.port.in.RemoveCartItemUseCase;
import com.philia.flashsale.cart.application.port.in.SetCartItemUseCase;
import com.philia.flashsale.cart.application.result.CartItemResult;
import com.philia.flashsale.cart.websupport.error.CartHttpExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class CartMutationHttpTests {
    private final SetCartItemUseCase setCartItem = mock(SetCartItemUseCase.class);
    private final RemoveCartItemUseCase removeCartItem = mock(RemoveCartItemUseCase.class);
    private final GetCartUseCase getCart = mock(GetCartUseCase.class);
    private MockMvc mvc;
    private JwtAuthenticationToken shopper;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CartController(setCartItem, removeCartItem,
                        getCart, new CartWebMapper()))
                .setControllerAdvice(new CartHttpExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
        UUID owner = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("shopper-token").header("typ", "at+jwt")
                .subject(owner.toString()).build();
        shopper = new JwtAuthenticationToken(jwt, java.util.List.of());
    }

    @Test
    void putReturnsEnvelopeNoStoreAndTraceHeader() throws Exception {
        UUID variant = UUID.randomUUID();
        when(setCartItem.set(any())).thenReturn(new CartItemResult(variant, 2, true, true, null,
                UUID.randomUUID(), "slug", "Product", "Variant", "SKU", null, "VND", null,
                Instant.parse("2026-08-29T00:00:00Z")));

        mvc.perform(put("/api/v1/cart/items/{variantId}", variant).principal(shopper)
                        .header("X-Trace-Id", "trace-cart")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Trace-Id", "trace-cart"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.variantId").value(variant.toString()))
                .andExpect(jsonPath("$.data.quantity").value(2));
    }

    @Test
    void deleteReturnsEmpty204AndMapsProductOutcomes() throws Exception {
        UUID variant = UUID.randomUUID();
        mvc.perform(delete("/api/v1/cart/items/{variantId}", variant).principal(shopper))
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        doThrow(new CartVariantNotFoundException()).when(setCartItem).set(any());
        mvc.perform(put("/api/v1/cart/items/{variantId}", variant).principal(shopper)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode")
                        .value("CART_VARIANT_NOT_FOUND"));

        doThrow(new CartVariantNotSellableException()).when(setCartItem).set(any());
        mvc.perform(put("/api/v1/cart/items/{variantId}", variant).principal(shopper)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isConflict());

        doThrow(new ProductDisplayDependencyException(
                ProductDisplayDependencyException.Failure.TIMEOUT)).when(setCartItem).set(any());
        mvc.perform(put("/api/v1/cart/items/{variantId}", variant).principal(shopper)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void invalidQuantityIsRejectedBeforeUseCase() throws Exception {
        mvc.perform(put("/api/v1/cart/items/{variantId}", UUID.randomUUID()).principal(shopper)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CART_VALIDATION_ERROR"));
    }
}
