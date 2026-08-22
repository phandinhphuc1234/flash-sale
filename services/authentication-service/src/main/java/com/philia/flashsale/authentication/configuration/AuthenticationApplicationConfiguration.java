package com.philia.flashsale.authentication.configuration;

import java.time.Clock;

import com.philia.flashsale.authentication.account.application.registration.RegisterAccountPort;
import com.philia.flashsale.authentication.account.application.registration.RegisterAccountService;
import com.philia.flashsale.authentication.account.application.registration.RegisterAccountUseCase;
import com.philia.flashsale.authentication.account.application.registration.EncodePasswordPort;
import com.philia.flashsale.authentication.account.application.bootstrap.AdminBootstrapAccountPort;
import com.philia.flashsale.authentication.account.application.bootstrap.BootstrapAdminAccountService;
import com.philia.flashsale.authentication.account.application.bootstrap.BootstrapAdminAccountUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.philia.flashsale.authentication.session.application.login.AuthenticateAccountUseCase;
import com.philia.flashsale.authentication.session.application.login.AuthenticateAccountService;
import com.philia.flashsale.authentication.session.application.login.LoadAccountForAuthenticationPort;
import com.philia.flashsale.authentication.session.application.login.LoginThrottlePort;
import com.philia.flashsale.authentication.session.application.login.PersistSuccessfulLoginPort;
import com.philia.flashsale.authentication.session.application.login.IssueLoginCredentialsPort;
import com.philia.flashsale.authentication.session.application.login.PasswordProofPort;
import com.philia.flashsale.authentication.security.token.SecureRefreshCredentialAdapter;
import com.philia.flashsale.authentication.security.token.Sha256RefreshCredentialDigestAdapter;
import com.philia.flashsale.authentication.session.application.refresh.RefreshSessionUseCase;
import com.philia.flashsale.authentication.session.application.refresh.RefreshSessionService;
import com.philia.flashsale.authentication.session.application.refresh.RotateRefreshCredentialPort;
import com.philia.flashsale.authentication.session.application.refresh.IssueRefreshedCredentialsPort;
import com.philia.flashsale.authentication.session.application.logout.LogoutSessionUseCase;
import com.philia.flashsale.authentication.session.application.logout.LogoutSessionService;
import com.philia.flashsale.authentication.session.application.logout.RevokeAuthenticationSessionsPort;

@Configuration
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "core-enabled", havingValue = "true", matchIfMissing = true)
/** Spring composition for authentication use cases; no business rule belongs here. */
public class AuthenticationApplicationConfiguration {

    @Bean
    Clock authenticationClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnBean({RegisterAccountPort.class, EncodePasswordPort.class})
    RegisterAccountUseCase registerAccountUseCase(RegisterAccountPort accountPort,
                                                   EncodePasswordPort passwordPort,
                                                   Clock authenticationClock) {
        return new RegisterAccountService(accountPort, passwordPort, authenticationClock);
    }

    @Bean
    @ConditionalOnBean({AdminBootstrapAccountPort.class, EncodePasswordPort.class})
    BootstrapAdminAccountUseCase bootstrapAdminAccountUseCase(AdminBootstrapAccountPort accountPort,
                                                               EncodePasswordPort passwordPort,
                                                               Clock authenticationClock) {
        return new BootstrapAdminAccountService(accountPort, passwordPort, authenticationClock);
    }

    @Bean
    @ConditionalOnBean({LoadAccountForAuthenticationPort.class, PasswordProofPort.class, LoginThrottlePort.class,
            PersistSuccessfulLoginPort.class, IssueLoginCredentialsPort.class})
    AuthenticateAccountUseCase authenticateAccountUseCase(LoadAccountForAuthenticationPort accountPort,
                                                          PasswordProofPort passwordProofPort,
                                                          LoginThrottlePort throttlePort,
                                                          PersistSuccessfulLoginPort persistencePort,
                                                          IssueLoginCredentialsPort credentialsPort,
                                                          SecureRefreshCredentialAdapter refreshGenerator,
                                                          Sha256RefreshCredentialDigestAdapter digestAdapter,
                                                          Clock authenticationClock) {
        return new AuthenticateAccountService(accountPort, passwordProofPort, throttlePort, persistencePort,
                credentialsPort, refreshGenerator, digestAdapter, authenticationClock);
    }

    @Bean
    @ConditionalOnBean({RotateRefreshCredentialPort.class, IssueRefreshedCredentialsPort.class})
    RefreshSessionUseCase refreshSessionUseCase(RotateRefreshCredentialPort rotationPort,
                                                IssueRefreshedCredentialsPort credentialsPort,
                                                SecureRefreshCredentialAdapter generator,
                                                Sha256RefreshCredentialDigestAdapter digestAdapter,
                                                Clock authenticationClock) {
        return new RefreshSessionService(rotationPort, credentialsPort, generator, digestAdapter, authenticationClock);
    }

    @Bean
    @ConditionalOnBean(RevokeAuthenticationSessionsPort.class)
    LogoutSessionUseCase logoutSessionUseCase(RevokeAuthenticationSessionsPort revokePort,
                                              Sha256RefreshCredentialDigestAdapter digestAdapter,
                                              Clock authenticationClock) {
        return new LogoutSessionService(revokePort, digestAdapter, authenticationClock);
    }
}
