package com.philia.flashsale.cart;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "cart.security.jwt.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "liquibase.integration.spring.boot3.autoconfigure.LiquibaseAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
})
class CartServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
