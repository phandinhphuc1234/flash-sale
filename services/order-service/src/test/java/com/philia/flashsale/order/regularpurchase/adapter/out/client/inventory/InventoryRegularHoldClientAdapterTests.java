package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.order.regularpurchase.application.exception.RegularPurchaseDownstreamException;
import com.philia.flashsale.order.regularpurchase.application.model.RegularStockHoldCommand;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Boundary tests prove Inventory request identities never change and timeouts remain ambiguous. */
class InventoryRegularHoldClientAdapterTests {

    private final InventoryRegularHoldFeignClient client = mock(InventoryRegularHoldFeignClient.class);
    private final InventoryRegularHoldClientAdapter adapter = new InventoryRegularHoldClientAdapter(client);

    @Test
    void preservesOneDurableHoldIdentityAndMapsTheExactHeldResponse() {
        RegularStockHoldCommand command = command();
        when(client.create(eq("trace-1"), any())).thenReturn(ApiResponse.success(response(command)));

        var hold = adapter.create(command, "trace-1");

        ArgumentCaptor<InventoryRegularHoldRequest> request = ArgumentCaptor.forClass(InventoryRegularHoldRequest.class);
        verify(client).create(eq("trace-1"), request.capture());
        assertThat(request.getValue().holdId()).isEqualTo(command.holdId());
        assertThat(request.getValue().purchaseRequestId()).isEqualTo(command.purchaseRequestId());
        assertThat(request.getValue().items()).extracting(item -> item.variantId())
                .containsExactly(command.items().getFirst().variantId());
        assertThat(hold.expiresAt()).isAfter(command.requestedAt());
    }

    @Test
    void mapsStableInsufficientStockToABusinessDecision() {
        when(client.create(any(), any())).thenThrow(new InventoryRegularHoldRemoteException(
                InventoryRegularHoldRemoteException.Failure.INSUFFICIENT_STOCK, 409));

        assertThatThrownBy(() -> adapter.create(command(), "trace-2"))
                .isInstanceOf(RegularPurchaseDownstreamException.class)
                .extracting(exception -> ((RegularPurchaseDownstreamException) exception).failure())
                .isEqualTo(RegularPurchaseDownstreamException.Failure.INVENTORY_INSUFFICIENT_STOCK);
    }

    @Test
    void treatsAnUncertainTransportOutcomeAsAmbiguousForSameIdentityResume() {
        Request request = Request.create(Request.HttpMethod.POST, "http://inventory/holds", Map.of(),
                new byte[0], StandardCharsets.UTF_8, null);
        FeignException timeout = FeignException.errorStatus("create", Response.builder()
                .status(504).reason("Gateway Timeout").request(request).build());
        when(client.create(any(), any())).thenThrow(timeout);

        assertThatThrownBy(() -> adapter.create(command(), "trace-3"))
                .isInstanceOf(RegularPurchaseDownstreamException.class)
                .extracting(exception -> ((RegularPurchaseDownstreamException) exception).failure())
                .isEqualTo(RegularPurchaseDownstreamException.Failure.INVENTORY_HOLD_AMBIGUOUS);
    }

    private RegularStockHoldCommand command() {
        return new RegularStockHoldCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-09-04T05:00:00Z"),
                List.of(new RegularStockHoldCommand.RegularStockHoldLine(UUID.randomUUID(), 2)));
    }

    private InventoryRegularHoldResponse response(RegularStockHoldCommand command) {
        return new InventoryRegularHoldResponse(command.holdId(), command.purchaseRequestId(), command.orderId(),
                "HELD", command.requestedAt().plusSeconds(300), command.items().stream()
                        .map(item -> new InventoryRegularHoldResponse.InventoryRegularHoldItemResponse(
                                item.variantId(), item.quantity()))
                        .toList());
    }
}
