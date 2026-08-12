package com.philia.flashsale.flashsale.campaignprojection.adapter.out.client.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.flashsale.campaignprojection.application.exception.CampaignSnapshotRecoveryException;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignProjectionState;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CampaignSnapshotClientAdapterTests {
    private static final Instant START = Instant.parse("2030-08-01T10:00:00Z");

    @Test
    void mapsRawSnapshotAndPropagatesAValidW3cTraceparent() {
        CampaignSnapshotFeignClient client = org.mockito.Mockito.mock(CampaignSnapshotFeignClient.class);
        UUID campaignId = UUID.randomUUID();
        when(client.getSnapshot(eq(campaignId), eq("application/json"), anyString(), anyString()))
                .thenReturn(response(campaignId, "SCHEDULED"));
        CampaignSnapshotClientAdapter adapter = new CampaignSnapshotClientAdapter(
                client, new CampaignSnapshotClientMapper());

        var projection = adapter.load(campaignId).orElseThrow();

        assertThat(projection.campaignId()).isEqualTo(campaignId);
        assertThat(projection.state()).isEqualTo(CampaignProjectionState.SCHEDULED);
        assertThat(projection.item().saleUnitPrice()).isEqualByComparingTo("19.9900");
        ArgumentCaptor<String> traceparent = ArgumentCaptor.forClass(String.class);
        verify(client).getSnapshot(eq(campaignId), eq("application/json"), traceparent.capture(), anyString());
        assertThat(traceparent.getValue()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
    }

    @Test
    void rejectsAResponseForAnotherCampaign() {
        CampaignSnapshotFeignClient client = org.mockito.Mockito.mock(CampaignSnapshotFeignClient.class);
        UUID requested = UUID.randomUUID();
        when(client.getSnapshot(eq(requested), eq("application/json"), anyString(), anyString()))
                .thenReturn(response(UUID.randomUUID(), "ACTIVE"));
        CampaignSnapshotClientAdapter adapter = new CampaignSnapshotClientAdapter(
                client, new CampaignSnapshotClientMapper());

        assertThatThrownBy(() -> adapter.load(requested))
                .isInstanceOf(CampaignSnapshotRecoveryException.class)
                .extracting("failure")
                .isEqualTo(CampaignSnapshotRecoveryException.Failure.INVALID_RESPONSE);
    }

    @Test
    void maps429RetryAfterWithoutLeakingFeignTypes() {
        CampaignSnapshotErrorDecoder decoder = new CampaignSnapshotErrorDecoder(new ObjectMapper());
        Response response = Response.builder()
                .status(429)
                .reason("Too Many Requests")
                .headers(Map.of("Retry-After", List.of("7")))
                .request(Request.create(Request.HttpMethod.GET, "/snapshot",
                        Map.of(), null, StandardCharsets.UTF_8, null))
                .build();

        Exception decoded = decoder.decode("CampaignSnapshotFeignClient#getSnapshot", response);

        assertThat(decoded).isInstanceOf(CampaignSnapshotRemoteException.class);
        CampaignSnapshotRemoteException remote = (CampaignSnapshotRemoteException) decoded;
        assertThat(remote.failure()).isEqualTo(CampaignSnapshotRemoteException.Failure.RATE_LIMITED);
        assertThat(remote.retryAfter()).hasSeconds(7);
    }

    private CampaignSnapshotClientResponse response(UUID campaignId, String state) {
        return new CampaignSnapshotClientResponse(campaignId, "G4", state, START,
                START.plusSeconds(3600), 3L,
                new CampaignSnapshotClientResponse.CampaignSnapshotItemResponse(
                        UUID.randomUUID(), UUID.randomUUID(), "SKU-G4", new BigDecimal("19.9900"),
                        "VND", 20L, 2L));
    }
}
