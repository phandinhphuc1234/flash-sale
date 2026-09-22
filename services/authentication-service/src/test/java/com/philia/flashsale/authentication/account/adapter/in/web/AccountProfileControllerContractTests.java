package com.philia.flashsale.authentication.account.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.philia.flashsale.authentication.account.application.profile.AccountProfile;
import com.philia.flashsale.authentication.account.application.profile.LoadAccountProfileUseCase;
import com.philia.flashsale.authentication.websupport.filter.AuthenticationTraceFilter;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class AccountProfileControllerContractTests {

    @Test
    void returnsSafeNoStoreProfileEnvelopeAndTraceHeader() throws Exception {
        UUID id = UUID.randomUUID();
        LoadAccountProfileUseCase useCase = mock(LoadAccountProfileUseCase.class);
        when(useCase.load(id)).thenReturn(new AccountProfile(id, "phuc", "phuc", "phuc@example.com", "Phuc",
                "ACTIVE", List.of("ROLE_USER")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AccountProfileController(useCase))
                .addFilters(new AuthenticationTraceFilter())
                .setCustomArgumentResolvers(new JwtArgumentResolver(id))
                .build();

        mvc.perform(get("/api/v1/auth/me").header("X-Trace-Id", "profile-contract"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "profile-contract"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.login").value("phuc"))
                .andExpect(jsonPath("$.data.username").value("phuc"))
                .andExpect(jsonPath("$.data.displayName").value("Phuc"))
                .andExpect(jsonPath("$.data.authorities[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshCredential").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    private static final class JwtArgumentResolver implements HandlerMethodArgumentResolver {
        private final Jwt jwt;

        private JwtArgumentResolver(UUID subject) {
            this.jwt = Jwt.withTokenValue("test-token").subject(subject.toString()).header("alg", "none").build();
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && Jwt.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return jwt;
        }
    }
}
