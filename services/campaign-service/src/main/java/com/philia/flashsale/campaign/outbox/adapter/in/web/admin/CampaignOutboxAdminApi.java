package com.philia.flashsale.campaign.outbox.adapter.in.web.admin;

import com.philia.flashsale.campaign.outbox.adapter.in.web.admin.request.RequeueCampaignOutboxEventRequest;
import com.philia.flashsale.campaign.outbox.adapter.in.web.admin.response.CampaignOutboxRequeueResponse;
import com.philia.flashsale.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** HTTP contract for operator recovery of a terminal Campaign lifecycle notification. */
public interface CampaignOutboxAdminApi {

    @Operation(summary = "Requeue a failed Campaign lifecycle event",
            description = "Requeues the same durable event identity after ten automatic failures.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Event returned to PENDING"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Event is not owned by the Campaign"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Event is not terminally failed")
    })
    @PostMapping("/api/v1/admin/campaigns/{campaignId}/outbox-events/{eventId}/requeue")
    ResponseEntity<ApiResponse<CampaignOutboxRequeueResponse>> requeue(
            @PathVariable UUID campaignId,
            @PathVariable UUID eventId,
            @RequestBody(required = false) RequeueCampaignOutboxEventRequest request,
            HttpServletRequest httpRequest);
}
