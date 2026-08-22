package com.philia.flashsale.authentication;

import com.philia.flashsale.authentication.configuration.AuthenticationProperties;
import com.philia.flashsale.authentication.configuration.AdminBootstrapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AuthenticationProperties.class, AdminBootstrapProperties.class})
/** Spring Boot composition root for the Authentication bounded context. */
public class AuthenticationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthenticationServiceApplication.class, args);
    }
}
