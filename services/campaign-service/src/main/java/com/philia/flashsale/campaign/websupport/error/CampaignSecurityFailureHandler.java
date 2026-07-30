package com.philia.flashsale.campaign.websupport.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.campaign.websupport.context.CampaignRequestContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Writes the same safe Campaign error body for resource-server authentication failures. */
@Component
public final class CampaignSecurityFailureHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CampaignSecurityFailureHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        writeError(request, response, CampaignErrorCode.UNAUTHENTICATED);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException, ServletException {
        CampaignErrorCode code = request.getRequestURI().startsWith("/api/v1/admin/campaigns")
                ? CampaignErrorCode.CAMPAIGN_ADMIN_REQUIRED
                : CampaignErrorCode.CAMPAIGN_ACCESS_DENIED;
        writeError(request, response, code);
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            CampaignErrorCode code) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String traceId = CampaignRequestContext.resolveTraceId(request);
        response.setStatus(code.status().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CampaignRequestContext.TRACE_HEADER, traceId);
        if (code == CampaignErrorCode.UNAUTHENTICATED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        objectMapper.writeValue(
                response.getOutputStream(),
                new CampaignErrorResponse(code.name(), code.message(), traceId, null));
    }
}
