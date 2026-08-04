package com.philia.flashsale.campaign.outbox.adapter.in.web.admin;

import com.philia.flashsale.campaign.outbox.adapter.in.web.admin.request.RequeueCampaignOutboxEventRequest;
import com.philia.flashsale.campaign.outbox.adapter.in.web.admin.response.CampaignOutboxRequeueResponse;
import com.philia.flashsale.campaign.outbox.application.usecase.RequeueCampaignOutboxEventService;
import com.philia.flashsale.campaign.websupport.context.CampaignRequestContext;
import com.philia.flashsale.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/** Drives the authenticated operator requeue use case and preserves the trace header. */
@RestController
public class CampaignOutboxAdminController implements CampaignOutboxAdminApi {

    private final RequeueCampaignOutboxEventService requeueService;

    public CampaignOutboxAdminController(RequeueCampaignOutboxEventService requeueService) {
        this.requeueService = requeueService;
    }

    @Override
    public ResponseEntity<ApiResponse<CampaignOutboxRequeueResponse>> requeue(
            UUID campaignId, UUID eventId, RequeueCampaignOutboxEventRequest request,
            HttpServletRequest httpRequest) {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        String actor = authentication == null ? "unknown-operator" : authentication.getName();
        var result = requeueService.requeue(campaignId, eventId, actor);
        String traceId = CampaignRequestContext.resolveTraceId(httpRequest);
        return ResponseEntity.ok()
                .header(CampaignRequestContext.TRACE_HEADER, traceId)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(CampaignOutboxRequeueResponse.from(result)));
    }
}
