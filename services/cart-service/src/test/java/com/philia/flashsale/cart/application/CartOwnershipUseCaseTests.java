package com.philia.flashsale.cart.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;

import com.philia.flashsale.cart.application.command.ClearCartCommand;
import com.philia.flashsale.cart.application.port.out.LoadProductDisplaysPort;
import com.philia.flashsale.cart.application.port.out.MaintainCartPort;
import com.philia.flashsale.cart.application.usecase.MaintainCartService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartOwnershipUseCaseTests {
    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    @Test
    void clearIsOwnerScopedAndRepeatableWithoutProductLookup() {
        LoadProductDisplaysPort products = mock(LoadProductDisplaysPort.class);
        MaintainCartPort cart = mock(MaintainCartPort.class);
        MaintainCartService service = new MaintainCartService(products, cart,
                Clock.fixed(NOW, ZoneOffset.UTC));
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();

        service.clear(new ClearCartCommand(firstOwner));
        service.clear(new ClearCartCommand(firstOwner));
        service.clear(new ClearCartCommand(secondOwner));

        verify(cart, org.mockito.Mockito.times(2)).clearItems(firstOwner, NOW);
        verify(cart).clearItems(secondOwner, NOW);
        verifyNoInteractions(products);
    }
}
