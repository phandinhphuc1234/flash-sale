package com.philia.flashsale.cart.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.cart.adapter.in.web.CartController;
import com.philia.flashsale.cart.adapter.in.web.CartWebMapper;
import com.philia.flashsale.cart.application.command.ClearCartCommand;
import com.philia.flashsale.cart.application.port.in.ClearCartUseCase;
import com.philia.flashsale.cart.application.port.in.GetCartUseCase;
import com.philia.flashsale.cart.application.port.in.RemoveCartItemUseCase;
import com.philia.flashsale.cart.application.port.in.SetCartItemUseCase;
import com.philia.flashsale.cart.websupport.error.CartHttpExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CartSecurityContractTests {
    private final SetCartItemUseCase setCartItem = mock(SetCartItemUseCase.class);
    private final RemoveCartItemUseCase removeCartItem = mock(RemoveCartItemUseCase.class);
    private final ClearCartUseCase clearCart = mock(ClearCartUseCase.class);
    private final GetCartUseCase getCart = mock(GetCartUseCase.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CartController(setCartItem, removeCartItem,
                        clearCart, getCart, new CartWebMapper()))
                .setControllerAdvice(new CartHttpExceptionHandler())
                .build();
    }

    @Test
    void clearUsesJwtOwnerAndIgnoresForgedOwnerInput() throws Exception {
        UUID authenticatedOwner = UUID.randomUUID();
        UUID forgedOwner = UUID.randomUUID();

        mvc.perform(delete("/api/v1/cart").principal(token(authenticatedOwner))
                        .queryParam("ownerId", forgedOwner.toString()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(clearCart).clear(new ClearCartCommand(authenticatedOwner));
    }

    @Test
    void everyClearRequiresAnAuthenticatedShopper() throws Exception {
        mvc.perform(delete("/api/v1/cart").header("X-Trace-Id", "trace-anonymous"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Trace-Id", "trace-anonymous"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    private static JwtAuthenticationToken token(UUID owner) {
        Jwt jwt = Jwt.withTokenValue("shopper-token").header("typ", "at+jwt")
                .subject(owner.toString()).build();
        return new JwtAuthenticationToken(jwt, List.of());
    }
}
