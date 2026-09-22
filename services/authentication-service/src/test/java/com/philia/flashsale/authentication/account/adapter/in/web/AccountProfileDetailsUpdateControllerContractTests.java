package com.philia.flashsale.authentication.account.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.philia.flashsale.authentication.account.application.profile.AccountProfile;
import com.philia.flashsale.authentication.account.application.profile.UpdateAccountDetailsCommand;
import com.philia.flashsale.authentication.account.application.profile.UpdateAccountDetailsUseCase;
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

class AccountProfileDetailsUpdateControllerContractTests {
    @Test
    void updatesVisibleDetailsWithNoStoreAndTrace() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateAccountDetailsUseCase useCase = mock(UpdateAccountDetailsUseCase.class);
        when(useCase.update(any())).thenReturn(new AccountProfile(id, "phuc.dev", "phuc.dev",
                "phuc@example.com", "phuc.dev", "Phuc Nguyen", "+84901234567", "Thu Duc",
                "ACTIVE", List.of("ROLE_USER")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AccountProfileDetailsUpdateController(useCase))
                .setControllerAdvice(new AuthenticationHttpExceptionHandler())
                .addFilters(new AuthenticationTraceFilter())
                .setCustomArgumentResolvers(new JwtArgumentResolver(id))
                .build();

        mvc.perform(patch("/api/v1/auth/me/profile")
                        .header("X-Trace-Id", "profile-details-contract")
                        .contentType("application/json")
                        .content("{\"username\":\"phuc.dev\",\"fullName\":\"Phuc Nguyen\",\"phone\":\"+84901234567\",\"address\":\"Thu Duc\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "profile-details-contract"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.data.fullName").value("Phuc Nguyen"))
                .andExpect(jsonPath("$.data.phone").value("+84901234567"))
                .andExpect(jsonPath("$.data.address").value("Thu Duc"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verify(useCase).update(any(UpdateAccountDetailsCommand.class));
    }

    @Test
    void rejectsUnknownOrEmptyProfilePayload() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateAccountDetailsUseCase useCase = mock(UpdateAccountDetailsUseCase.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AccountProfileDetailsUpdateController(useCase))
                .setControllerAdvice(new AuthenticationHttpExceptionHandler())
                .addFilters(new AuthenticationTraceFilter())
                .setCustomArgumentResolvers(new JwtArgumentResolver(id))
                .build();

        mvc.perform(patch("/api/v1/auth/me/profile").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_VALIDATION_FAILED"));
        mvc.perform(patch("/api/v1/auth/me/profile").contentType("application/json")
                        .content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AUTH_VALIDATION_FAILED"));
    }

    private static final class JwtArgumentResolver implements HandlerMethodArgumentResolver {
        private final Jwt jwt;
        private JwtArgumentResolver(UUID subject) {
            jwt = Jwt.withTokenValue("test-token").subject(subject.toString()).header("alg", "none").build();
        }
        @Override public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && Jwt.class.isAssignableFrom(parameter.getParameterType());
        }
        @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return jwt;
        }
    }
}
