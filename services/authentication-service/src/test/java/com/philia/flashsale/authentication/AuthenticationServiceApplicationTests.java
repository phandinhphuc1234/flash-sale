package com.philia.flashsale.authentication;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "flashsale.authentication.http.enabled=false",
        "flashsale.authentication.core-enabled=false",
        "flashsale.authentication.runtime-enabled=false"
})
@ImportAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        LiquibaseAutoConfiguration.class
})
class AuthenticationServiceApplicationTests {

    private static final String PUBLIC_KEY_PEM = createPublicKeyPem();

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("flashsale.auth.jwt.public-key-pem", () -> PUBLIC_KEY_PEM);
    }

    @Test
    void contextLoads() {
    }

    private static String createPublicKeyPem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes())
                            .encodeToString(keyPair.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----";
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create test key", exception);
        }
    }
}
