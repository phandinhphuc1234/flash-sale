package com.philia.flashsale.campaign.websupport.filter;

import com.philia.flashsale.campaign.websupport.context.CampaignRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Creates or propagates the bounded X-Trace-Id and echoes it in every Campaign response. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CampaignTraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = CampaignRequestContext.normalizeOrGenerate(
                request.getHeader(CampaignRequestContext.TRACE_HEADER));
        request.setAttribute(CampaignRequestContext.TRACE_ATTRIBUTE, traceId);
        response.setHeader(CampaignRequestContext.TRACE_HEADER, traceId);
        filterChain.doFilter(request, response);
    }
}
