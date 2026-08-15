package com.philia.flashsale.order.configuration;

import com.philia.flashsale.order.order.adapter.out.identity.OrderIdentityAdapter;
import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

/** Framework wiring for replaceable identity and clock ports. */
@Configuration
public class OrderFoundationConfiguration {

    @Bean
    public Clock orderClock() {
        return Clock.systemUTC();
    }

    @Bean
    public OrderIdentityAdapter orderIdentityAdapter(Clock orderClock) {
        return new OrderIdentityAdapter(orderClock);
    }

    @Bean
    public GenerateOrderIdentityPort generateOrderIdentityPort(
            @Qualifier("orderIdentityAdapter") OrderIdentityAdapter adapter) {
        return adapter;
    }

    @Bean
    public GenerateOrderNumberPort generateOrderNumberPort(
            @Qualifier("orderIdentityAdapter") OrderIdentityAdapter adapter) {
        return adapter;
    }

    @Bean
    public CurrentTimePort currentTimePort(
            @Qualifier("orderIdentityAdapter") OrderIdentityAdapter adapter) {
        return adapter;
    }
}
