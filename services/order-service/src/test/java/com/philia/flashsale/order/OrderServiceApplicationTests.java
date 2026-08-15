package com.philia.flashsale.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "order.creation.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "liquibase.integration.spring.boot3.autoconfigure.LiquibaseAutoConfiguration"
})
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
