package com.philia.flashsale.flashsale.campaignprojection.adapter.out.client.campaign;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/** Transport-only declaration of the private Campaign snapshot contract. */
@FeignClient(
        name = "flashsale-campaign",
        contextId = "campaignSnapshotFeignClient",
        url = "${flashsale.campaign-projection.snapshot-base-url}",
        configuration = CampaignSnapshotFeignConfiguration.class)
public interface CampaignSnapshotFeignClient {

    @GetMapping(
            path = "/internal/v1/campaigns/{campaignId}/snapshot",
            produces = MediaType.APPLICATION_JSON_VALUE)
    CampaignSnapshotClientResponse getSnapshot(
            @PathVariable UUID campaignId,
            @RequestHeader(HttpHeaders.ACCEPT) String accept,
            @RequestHeader("traceparent") String traceparent,
            @RequestHeader("X-Trace-Id") String traceId);
}
