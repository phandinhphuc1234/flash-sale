package com.philia.flashsale.campaign.campaign.adapter.in.web.admin;

import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.mapper.CampaignAdminWebMapper;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.CreateCampaignRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.ActivateCampaignRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.ReplaceCampaignItemRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.ReplaceCampaignMetadataRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request.ScheduleCampaignRequest;
import com.philia.flashsale.campaign.campaign.adapter.in.web.admin.response.CampaignResponse;
import com.philia.flashsale.campaign.campaign.application.port.in.CreateCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.ActivateCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.GetCampaignDetailUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.ReplaceCampaignItemUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.ReplaceCampaignMetadataUseCase;
import com.philia.flashsale.campaign.campaign.application.port.in.ScheduleCampaignUseCase;
import com.philia.flashsale.campaign.campaign.application.command.ScheduleCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.command.ActivateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.query.GetCampaignDetailQuery;
import com.philia.flashsale.campaign.campaign.application.result.CampaignDetailResult;
import com.philia.flashsale.common.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.philia.flashsale.campaign.websupport.context.CampaignRequestContext;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignActorPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;

/** Driving HTTP adapter for local Campaign draft administration. */
@RestController
@RequestMapping("/api/v1/admin/campaigns")
public class CampaignAdminController implements CampaignAdminApi {

    private final CreateCampaignUseCase createCampaign;
    private final ActivateCampaignUseCase activateCampaign;
    private final ReplaceCampaignMetadataUseCase replaceMetadata;
    private final ReplaceCampaignItemUseCase replaceItem;
    private final GetCampaignDetailUseCase getDetail;
    private final ScheduleCampaignUseCase scheduleCampaign;
    private final CampaignAdminWebMapper mapper;
    private final CampaignActorPort actorPort;
    private final CampaignClockPort clockPort;

    public CampaignAdminController(
            CreateCampaignUseCase createCampaign,
            ActivateCampaignUseCase activateCampaign,
            ReplaceCampaignMetadataUseCase replaceMetadata,
            ReplaceCampaignItemUseCase replaceItem,
            GetCampaignDetailUseCase getDetail,
            ScheduleCampaignUseCase scheduleCampaign,
            CampaignAdminWebMapper mapper,
            CampaignActorPort actorPort,
            CampaignClockPort clockPort) {
        this.createCampaign = createCampaign;
        this.activateCampaign = activateCampaign;
        this.replaceMetadata = replaceMetadata;
        this.replaceItem = replaceItem;
        this.getDetail = getDetail;
        this.scheduleCampaign = scheduleCampaign;
        this.mapper = mapper;
        this.actorPort = actorPort;
        this.clockPort = clockPort;
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<CampaignResponse>> create(
            @Valid @RequestBody CreateCampaignRequest request) {
        CampaignDetailResult result = createCampaign.create(mapper.toCommand(request));
        return ResponseEntity.status(201)
                .header(HttpHeaders.LOCATION, "/api/v1/admin/campaigns/" + result.id())
                .header(HttpHeaders.ETAG, etag(result.version()))
                .body(ApiResponse.success("Campaign created", mapper.toResponse(result)));
    }

    @Override
    @PatchMapping("/{campaignId}")
    public ResponseEntity<ApiResponse<CampaignResponse>> replaceMetadata(
            @PathVariable UUID campaignId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody ReplaceCampaignMetadataRequest request) {
        CampaignDetailResult result = replaceMetadata.replaceMetadata(
                mapper.toCommand(campaignId, parseVersion(ifMatch), request));
        return response(result);
    }

    @Override
    @PutMapping("/{campaignId}/item")
    public ResponseEntity<ApiResponse<CampaignResponse>> replaceItem(
            @PathVariable UUID campaignId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody ReplaceCampaignItemRequest request) {
        CampaignDetailResult result = replaceItem.replaceItem(
                mapper.toCommand(campaignId, parseVersion(ifMatch), request));
        return response(result);
    }

    @Override
    @GetMapping("/{campaignId}")
    public ResponseEntity<ApiResponse<CampaignResponse>> getDetail(@PathVariable UUID campaignId) {
        CampaignDetailResult result = getDetail.getDetail(new GetCampaignDetailQuery(campaignId));
        return response(result);
    }

    @Override
    @PostMapping("/{campaignId}/schedule")
    public ResponseEntity<ApiResponse<CampaignResponse>> schedule(
            @PathVariable UUID campaignId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ScheduleCampaignRequest request,
            @RequestHeader(value = CampaignRequestContext.TRACE_HEADER, required = false) String traceId) {
        CampaignDetailResult result = scheduleCampaign.schedule(
                new ScheduleCampaignCommand(campaignId, parseVersion(ifMatch), idempotencyKey,
                        "campaign-service", CampaignRequestContext.normalizeOrGenerate(traceId)));
        return response(result);
    }

    @Override
    @PostMapping("/{campaignId}/activate")
    public ResponseEntity<ApiResponse<CampaignResponse>> activate(
            @PathVariable UUID campaignId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody ActivateCampaignRequest request,
            @RequestHeader(value = CampaignRequestContext.TRACE_HEADER, required = false) String traceId) {
        CampaignDetailResult result = activateCampaign.activate(new ActivateCampaignCommand(
                campaignId,
                parseVersion(ifMatch),
                actorPort.currentActor(),
                clockPort.now(),
                CampaignRequestContext.normalizeOrGenerate(traceId),
                true)).campaign();
        return response(result);
    }

    private ResponseEntity<ApiResponse<CampaignResponse>> response(CampaignDetailResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, etag(result.version()))
                .body(ApiResponse.success(mapper.toResponse(result)));
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static long parseVersion(String ifMatch) {
        String value = ifMatch == null ? "" : ifMatch.trim();
        if (value.length() < 3 || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
            throw new IllegalArgumentException("If-Match must contain a quoted numeric Campaign version");
        }
        try {
            long version = Long.parseLong(value.substring(1, value.length() - 1));
            if (version < 0) {
                throw new IllegalArgumentException("If-Match version must not be negative");
            }
            return version;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match must contain a quoted numeric Campaign version", exception);
        }
    }
}
