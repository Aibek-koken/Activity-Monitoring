package com.easylang.activitymonitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ActivityMonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(ActivityMonitoringApplication.class, args);
    }
}

