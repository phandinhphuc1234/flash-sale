package com.philia.flashsale.authentication.session.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.authentication.configuration.AuthenticationProperties;
import com.philia.flashsale.authentication.session.application.login.AuthenticateAccountUseCase;
import com.philia.flashsale.authentication.session.application.login.AuthenticationResult;
import com.philia.flashsale.authentication.websupport.filter.AuthenticationTraceFilter;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerContractTests {

    @Test
    void loginUsesSharedSuccessEnvelopeAndKeepsRefreshCredentialInCookie() throws Exception {
        AuthenticateAccountUseCase useCase = mock(AuthenticateAccountUseCase.class);
        when(useCase.authenticate(any())).thenReturn(
                new AuthenticationResult("Bearer", "access-token", 900, "refresh-secret", 3600));
        LoginController controller = new LoginController(useCase,
                new RefreshCookieWriter(properties()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new AuthenticationTraceFilter())
                .build();

        mvc.perform(post("/api/v1/auth/login")
                        .header("X-Trace-Id", "auth-login-contract")
                        .contentType("application/json")
                        .content("""
                                {"login":"user@example.com","password":"correct","deviceName":"browser"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "auth-login-contract"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Set-Cookie", Matchers.containsString("refresh_token=")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
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
