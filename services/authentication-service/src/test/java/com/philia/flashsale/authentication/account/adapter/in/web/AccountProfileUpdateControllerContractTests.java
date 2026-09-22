package com.philia.flashsale.authentication.account.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.authentication.account.application.profile.AccountProfile;
import com.philia.flashsale.authentication.account.application.profile.UpdateAccountProfileUseCase;
import com.philia.flashsale.authentication.websupport.error.AuthenticationHttpExceptionHandler;
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

class AccountProfileUpdateControllerContractTests {

    @Test
    void returnsUpdatedSafeProfileWithTraceAndNoStore() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateAccountProfileUseCase useCase = mock(UpdateAccountProfileUseCase.class);
        when(useCase.update(any())).thenReturn(new AccountProfile(id, "phuc.dev", "phuc.dev",
                "phuc@example.com", "phuc.dev", "ACTIVE", List.of("ROLE_USER")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AccountProfileUpdateController(useCase))
                .addFilters(new AuthenticationTraceFilter())
                .setCustomArgumentResolvers(new JwtArgumentResolver(id))
                .build();

        mvc.perform(patch("/api/v1/auth/me")
                        .header("X-Trace-Id", "profile-update-contract")
                        .contentType("application/json")
                        .content(new ObjectMapper().writeValueAsString(new UpdateAccountProfileRequestPayload("phuc.dev"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "profile-update-contract"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("phuc.dev"))
                .andExpect(jsonPath("$.data.email").value("phuc@example.com"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void rejectsEmailAsAnUnknownEditableField() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateAccountProfileUseCase useCase = mock(UpdateAccountProfileUseCase.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AccountProfileUpdateController(useCase))
                .setControllerAdvice(new AuthenticationHttpExceptionHandler())
                .addFilters(new AuthenticationTraceFilter())
                .setCustomArgumentResolvers(new JwtArgumentResolver(id))
                .build();

        mvc.perform(patch("/api/v1/auth/me")
                        .contentType("application/json")
                        .content("{\"username\":\"phuc.dev\",\"email\":\"new@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_VALIDATION_FAILED"));
        verify(useCase, never()).update(any());
    }

    private record UpdateAccountProfileRequestPayload(String username) { }

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
