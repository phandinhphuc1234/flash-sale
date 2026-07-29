package com.philia.flashsale.authentication.session.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.philia.flashsale.authentication.configuration.AuthenticationProperties;
import com.philia.flashsale.authentication.session.application.refresh.RefreshCredentialIssuanceUnavailableException;
import com.philia.flashsale.authentication.session.application.refresh.RefreshSessionUseCase;
import com.philia.flashsale.authentication.websupport.context.AuthenticationRequestContext;
import com.philia.flashsale.authentication.websupport.error.AuthenticationHttpExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RefreshControllerTests {

    @Test
    void signingFailureClearsRefreshCookieAndReturnsServiceUnavailable() throws Exception {
        RefreshSessionUseCase useCase = mock(RefreshSessionUseCase.class);
        when(useCase.refresh(any())).thenThrow(
                new RefreshCredentialIssuanceUnavailableException(new IllegalStateException("signing failed")));
        RefreshController controller = new RefreshController(useCase, new RefreshCookieWriter(properties()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AuthenticationHttpExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "old-credential"))
                        .requestAttr(AuthenticationRequestContext.TRACE_ATTRIBUTE, "trace-refresh-signing"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.traceId").value("trace-refresh-signing"));
    }

    private AuthenticationProperties properties() {
        return new AuthenticationProperties(
                new AuthenticationProperties.Cookie(false, "Lax", "/api/v1/auth"),
                "https://app.example",
                new AuthenticationProperties.Throttle("secret", Duration.ofMinutes(15), Duration.ofMinutes(15), 5),
                new AuthenticationProperties.Retention(30),
                "0 0 3 * * *",
                new AuthenticationProperties.Argon2(16, 32, 1, 19_456, 2));
    }
}
