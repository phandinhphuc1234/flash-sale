package com.philia.flashsale.payment.payment.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CheckoutWebMapperWiringTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CheckoutWebMapper.class);

    @Test
    void exposesTheCheckoutMapperAsAnHttpAdapterBean() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CheckoutWebMapper.class);
        });
    }
}
