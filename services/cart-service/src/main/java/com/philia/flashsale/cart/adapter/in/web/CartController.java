package com.philia.flashsale.cart.adapter.in.web;

import com.philia.flashsale.cart.application.command.ClearCartCommand;
import com.philia.flashsale.cart.application.command.RemoveCartItemCommand;
import com.philia.flashsale.cart.application.command.SetCartItemCommand;
import com.philia.flashsale.cart.application.port.in.ClearCartUseCase;
import com.philia.flashsale.cart.application.port.in.GetCartUseCase;
import com.philia.flashsale.cart.application.port.in.RemoveCartItemUseCase;
import com.philia.flashsale.cart.application.port.in.SetCartItemUseCase;
import com.philia.flashsale.cart.application.query.GetCartQuery;
import com.philia.flashsale.cart.security.AuthenticatedShopper;
import com.philia.flashsale.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public authenticated Cart web adapter; owner identity is always derived from JWT. */
@RestController
@RequestMapping("/api/v1/cart")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean({SetCartItemUseCase.class, RemoveCartItemUseCase.class, ClearCartUseCase.class,
        GetCartUseCase.class})
public class CartController {
    public static final String TRACE_HEADER = "X-Trace-Id";

    private final SetCartItemUseCase setCartItem;
    private final RemoveCartItemUseCase removeCartItem;
    private final ClearCartUseCase clearCart;
    private final GetCartUseCase getCart;
    private final CartWebMapper mapper;

    public CartController(SetCartItemUseCase setCartItem, RemoveCartItemUseCase removeCartItem,
            ClearCartUseCase clearCart, GetCartUseCase getCart, CartWebMapper mapper) {
        this.setCartItem = setCartItem;
        this.removeCartItem = removeCartItem;
        this.clearCart = clearCart;
        this.getCart = getCart;
        this.mapper = mapper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> get(Authentication authentication,
            HttpServletRequest httpRequest) {
        AuthenticatedShopper shopper = AuthenticatedShopper.from(authentication);
        var result = getCart.get(new GetCartQuery(shopper.subject(), traceId(httpRequest)));
        return ResponseEntity.ok().headers(headers(httpRequest))
                .body(ApiResponse.success(mapper.toResponse(result)));
    }

    @PutMapping("/items/{variantId}")
    public ResponseEntity<ApiResponse<CartItemResponse>> set(
            @PathVariable UUID variantId, @Valid @RequestBody SetCartItemRequest request,
            Authentication authentication, HttpServletRequest httpRequest) {
        AuthenticatedShopper shopper = AuthenticatedShopper.from(authentication);
        var result = setCartItem.set(new SetCartItemCommand(shopper.subject(), variantId,
                request.quantity(), traceId(httpRequest)));
        return ResponseEntity.ok().headers(headers(httpRequest))
                .body(ApiResponse.success("Cart item saved", mapper.toResponse(result)));
    }

    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<Void> remove(@PathVariable UUID variantId,
            Authentication authentication, HttpServletRequest httpRequest) {
        AuthenticatedShopper shopper = AuthenticatedShopper.from(authentication);
        removeCartItem.remove(new RemoveCartItemCommand(shopper.subject(), variantId));
        return ResponseEntity.noContent().headers(headers(httpRequest)).build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(Authentication authentication, HttpServletRequest httpRequest) {
        AuthenticatedShopper shopper = AuthenticatedShopper.from(authentication);
        clearCart.clear(new ClearCartCommand(shopper.subject()));
        return ResponseEntity.noContent().headers(headers(httpRequest)).build();
    }

    private HttpHeaders headers(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.set(TRACE_HEADER, traceId(request));
        return headers;
    }

    private String traceId(HttpServletRequest request) {
        String value = request.getHeader(TRACE_HEADER);
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }
}
