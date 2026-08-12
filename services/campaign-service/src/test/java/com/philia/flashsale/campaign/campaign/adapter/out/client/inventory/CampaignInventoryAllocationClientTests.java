package com.philia.flashsale.campaign.campaign.adapter.out.client.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.campaign.campaign.application.command.AllocateCampaignInventoryCommand;
import com.philia.flashsale.campaign.campaign.application.exception.CampaignDownstreamException;
import com.philia.flashsale.campaign.campaign.application.result.CampaignInventoryAllocation;
import com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenManager;
import com.philia.flashsale.campaign.security.serviceidentity.CampaignServiceTokenManager.Capability;
import com.philia.flashsale.common.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CampaignInventoryAllocationClientTests {

    @Mock
    private InventoryFeignClient feignClient;
    @Mock
    private CampaignServiceTokenManager tokenManager;

    private CampaignInventoryAllocationClient adapter;

    @BeforeEach
    void setUp() {
        adapter = new CampaignInventoryAllocationClient(
                feignClient,
                Mappers.getMapper(InventoryAllocationClientMapper.class),
                tokenManager);
    }

    @Test
    void preservesRequestIdentityAndAcceptsOnlyACompleteActiveAllocation() {
        AllocateCampaignInventoryCommand command = command(100);
        UUID allocationId = UUID.randomUUID();
        InventoryAllocationResponse remote = new InventoryAllocationResponse(
                allocationId, command.requestId(), command.campaignId(), command.variantId(),
                command.quantity(), 0, 0, "ACTIVE");
        when(tokenManager.authorizationHeader(Capability.INVENTORY_ALLOCATION))
                .thenReturn("Bearer inventory-token");
        InventoryAllocationRequest request = new InventoryAllocationRequest(
                command.requestId(), command.campaignId(), command.variantId(),
                command.quantity(), "CAMPAIGN_SCHEDULE");
        when(feignClient.allocate("Bearer inventory-token", "trace-inventory", request))
                .thenReturn(ApiResponse.success("Allocation accepted", remote));

        CampaignInventoryAllocation result = adapter.allocate(command, "trace-inventory");

        assertThat(result.inventoryAllocationId()).isEqualTo(allocationId);
        assertThat(result.requestId()).isEqualTo(command.requestId());
        assertThat(result.allocatedQuantity()).isEqualTo(100);
        verify(feignClient).allocate("Bearer inventory-token", "trace-inventory", request);
    }

    @Test
    void rejectsAllocationResponseWhoseRequestIdentityDoesNotMatch() {
        AllocateCampaignInventoryCommand command = command(100);
        when(tokenManager.authorizationHeader(Capability.INVENTORY_ALLOCATION))
                .thenReturn("Bearer inventory-token");
        InventoryAllocationRequest request = new InventoryAllocationRequest(
                command.requestId(), command.campaignId(), command.variantId(),
                command.quantity(), "CAMPAIGN_SCHEDULE");
        InventoryAllocationResponse remote = new InventoryAllocationResponse(
                UUID.randomUUID(), UUID.randomUUID(), command.campaignId(), command.variantId(),
                command.quantity(), 0, 0, "ACTIVE");
        when(feignClient.allocate("Bearer inventory-token", "trace-inventory", request))
                .thenReturn(ApiResponse.success("Allocation accepted", remote));

        assertThatThrownBy(() -> adapter.allocate(command, "trace-inventory"))
                .isInstanceOf(CampaignDownstreamException.class)
                .extracting(exception -> ((CampaignDownstreamException) exception).failure())
                .isEqualTo(CampaignDownstreamException.Failure.INVENTORY_ALLOCATION_REJECTED);
    }

    @Test
    void mapsStableInventoryConflictWithoutUsingItsMessage() {
        AllocateCampaignInventoryCommand command = command(100);
        when(tokenManager.authorizationHeader(Capability.INVENTORY_ALLOCATION))
                .thenReturn("Bearer inventory-token");
        InventoryAllocationRequest request = new InventoryAllocationRequest(
                command.requestId(), command.campaignId(), command.variantId(),
                command.quantity(), "CAMPAIGN_SCHEDULE");
        InventoryRemoteException remoteFailure = (InventoryRemoteException)
                new InventoryFeignErrorDecoder(new ObjectMapper()).decode(
                        "InventoryFeignClient#allocate",
                        response(409,
                                "{\"errorCode\":\"INVENTORY_ALLOCATION_REQUEST_CONFLICT\",\"message\":\"ignored\"}"));
        when(feignClient.allocate("Bearer inventory-token", "trace-inventory", request))
                .thenThrow(remoteFailure);

        assertThatThrownBy(() -> adapter.allocate(command, "trace-inventory"))
                .isInstanceOf(CampaignDownstreamException.class)
                .extracting(exception -> ((CampaignDownstreamException) exception).failure())
                .isEqualTo(CampaignDownstreamException.Failure.INVENTORY_ALLOCATION_CONFLICT);
    }

    private AllocateCampaignInventoryCommand command(long quantity) {
        return new AllocateCampaignInventoryCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), quantity);
    }

    private Response response(int status, String body) {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://inventory-service/internal/v1/campaign-stock-allocations",
                Map.of(),
                (byte[]) null,
                StandardCharsets.UTF_8,
                null);
        return Response.builder()
                .request(request)
                .status(status)
                .reason("downstream failure")
                .body(body, StandardCharsets.UTF_8)
                .build();
    }
}
