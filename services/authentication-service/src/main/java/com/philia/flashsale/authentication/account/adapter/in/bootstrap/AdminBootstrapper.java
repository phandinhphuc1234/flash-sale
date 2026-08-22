package com.philia.flashsale.authentication.account.adapter.in.bootstrap;

import com.philia.flashsale.authentication.account.application.bootstrap.BootstrapAdminAccountCommand;
import com.philia.flashsale.authentication.account.application.bootstrap.BootstrapAdminAccountResult;
import com.philia.flashsale.authentication.account.application.bootstrap.BootstrapAdminAccountUseCase;
import com.philia.flashsale.authentication.configuration.AdminBootstrapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Inbound operational adapter for a single explicitly enabled administrator bootstrap Job. */
@Component
@ConditionalOnProperty(prefix = "flashsale.authentication.admin-bootstrap", name = "enabled",
        havingValue = "true")
public final class AdminBootstrapper implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrapper.class);

    private final BootstrapAdminAccountUseCase useCase;
    private final AdminBootstrapProperties properties;

    public AdminBootstrapper(BootstrapAdminAccountUseCase useCase, AdminBootstrapProperties properties) {
        this.useCase = useCase;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        BootstrapAdminAccountResult result = useCase.bootstrap(new BootstrapAdminAccountCommand(
                properties.email(), properties.username(), properties.password()));
        LOG.info("Authentication admin bootstrap completed: created={} accountId={}",
                result.created(), result.accountId());
    }
}
