package com.philia.flashsale.campaign.configuration;

import com.philia.flashsale.campaign.scheduleoperation.application.port.out.LoadScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.SaveScheduleOperationPort;
import com.philia.flashsale.campaign.scheduleoperation.application.port.out.ScheduleOperationClockPort;
import com.philia.flashsale.campaign.scheduleoperation.application.usecase.PrepareScheduleOperationService;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition-root wiring for the local schedule-operation preparation boundary. */
@Configuration
public class ScheduleOperationConfiguration {

    @Bean
    ScheduleOperationClockPort scheduleOperationClockPort() {
        return Instant::now;
    }

    @Bean
    PrepareScheduleOperationService prepareScheduleOperationService(
            LoadScheduleOperationPort loadPort,
            SaveScheduleOperationPort savePort,
            ScheduleOperationClockPort clockPort) {
        return new PrepareScheduleOperationService(loadPort, savePort, clockPort);
    }
}
