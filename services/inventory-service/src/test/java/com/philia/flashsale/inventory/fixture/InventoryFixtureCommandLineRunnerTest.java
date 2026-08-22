package com.philia.flashsale.inventory.fixture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.philia.flashsale.inventory.adapter.in.fixture.InventoryFixtureCommandLineRunner;
import com.philia.flashsale.inventory.configuration.InventoryFixtureProperties;
import com.philia.flashsale.inventory.stock.application.port.in.InitializeInventoryUseCase;
import com.philia.flashsale.inventory.stock.application.result.InventoryResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class InventoryFixtureCommandLineRunnerTest {
    @Test
    void delegatesValidatedFixtureToInventoryUseCaseAndClosesContext() {
        InitializeInventoryUseCase useCase = mock(InitializeInventoryUseCase.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        InventoryFixtureProperties properties = validProperties();
        UUID variantId = properties.getVariantId();
        when(useCase.initialize(any())).thenReturn(new InventoryResult(
                UUID.randomUUID(), variantId, properties.getSkuSnapshot(), 1, 0, 1));

        new InventoryFixtureCommandLineRunner(useCase, properties, context).run();

        verify(useCase).initialize(any());
        verify(context).close();
    }

    @Test
    void rejectsMissingVariantBeforeCallingUseCaseAndStillClosesContext() {
        InitializeInventoryUseCase useCase = mock(InitializeInventoryUseCase.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        InventoryFixtureProperties properties = validProperties();
        properties.setVariantId(null);

        assertThrows(IllegalArgumentException.class,
                () -> new InventoryFixtureCommandLineRunner(useCase, properties, context).run());

        verifyNoInteractions(useCase);
        verify(context).close();
    }

    @Test
    void disabledPropertiesDoNotRequireFixtureInputs() {
        InventoryFixtureProperties properties = new InventoryFixtureProperties();

        properties.validate();
    }

    private InventoryFixtureProperties validProperties() {
        InventoryFixtureProperties properties = new InventoryFixtureProperties();
        properties.setEnabled(true);
        properties.setVariantId(UUID.randomUUID());
        properties.setSkuSnapshot("PHASE22-SKU");
        properties.setQuantity(1);
        properties.setReason("PHASE22_INTERNAL_E2E");
        return properties;
    }
}
