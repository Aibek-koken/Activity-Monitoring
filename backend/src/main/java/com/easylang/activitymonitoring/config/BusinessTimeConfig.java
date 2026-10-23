package com.easylang.activitymonitoring.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessTimeConfig {

    @Bean
    Clock businessClock(@Value("${app.business-zone:Asia/Almaty}") String businessZone) {
        return Clock.system(ZoneId.of(businessZone));
    }
}
