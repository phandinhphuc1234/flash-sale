package com.philia.flashsale.inventory.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.inventory.configuration.InventoryTraceIdFilter;
import com.philia.flashsale.inventory.stock.adapter.in.web.InventoryAdminController;
import com.philia.flashsale.inventory.stock.adapter.in.web.mapper.InventoryWebMapper;
import com.philia.flashsale.inventory.stock.application.exception.StockApplicationException;
import com.philia.flashsale.inventory.stock.application.port.in.AdjustStockUseCase;
import com.philia.flashsale.inventory.stock.application.port.in.GetInventoryUseCase;
import com.philia.flashsale.inventory.stock.application.result.InventoryResult;
import com.philia.flashsale.inventory.websupport.error.InventoryExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class InventoryHttpContractTests {

    private static final String TRACE_ID = "inventory-contract-trace";
    private final GetInventoryUseCase getInventory = mock(GetInventoryUseCase.class);
    private final AdjustStockUseCase adjustStock = mock(AdjustStockUseCase.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InventoryAdminController controller = new InventoryAdminController(
                getInventory, adjustStock, new InventoryWebMapper());
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new InventoryExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .addFilters(new InventoryTraceIdFilter())
                .build();
    }

    @Test
    void successUsesSharedEnvelopeAndHeaderOnlyTrace() throws Exception {
        UUID variantId = UUID.randomUUID();
        when(getInventory.get(any())).thenReturn(new InventoryResult(
                UUID.randomUUID(), variantId, "SKU-1", 10, 2, 8));

        mockMvc.perform(get("/api/v1/admin/inventory/{variantId}", variantId)
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void validationUsesSharedErrorEnvelopeAndFieldViolations() throws Exception {
        mockMvc.perform(post("/api/v1/admin/inventory/{variantId}/adjustments", UUID.randomUUID())
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void notFoundUsesInventoryOwnedErrorCode() throws Exception {
        when(getInventory.get(any())).thenThrow(StockApplicationException.notFound("missing"));

        mockMvc.perform(get("/api/v1/admin/inventory/{variantId}", UUID.randomUUID())
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.errorCode").value("INVENTORY_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void rejectedCommandUsesConflictAndInventoryOwnedErrorCode() throws Exception {
        when(adjustStock.adjust(any())).thenThrow(new StockApplicationException("insufficient"));

        mockMvc.perform(post("/api/v1/admin/inventory/{variantId}/adjustments", UUID.randomUUID())
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                  "requestId": "%s",
                                  "type": "INCREASE",
                                  "quantity": 1,
                                  "reason": "contract test"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.errorCode").value("INVENTORY_OPERATION_REJECTED"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void malformedJsonUsesSafeValidationEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/admin/inventory/{variantId}/adjustments", UUID.randomUUID())
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("{\"requestId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void missingTraceHeaderGeneratesOneWithoutAddingItToJson() throws Exception {
        when(getInventory.get(any())).thenReturn(new InventoryResult(
                UUID.randomUUID(), UUID.randomUUID(), "SKU-1", 1, 0, 1));

        mockMvc.perform(get("/api/v1/admin/inventory/{variantId}", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }
}
