package com.monitoring.poc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * UserDetailsServiceAutoConfiguration is excluded because auth is handled
 * entirely by AuthService (manual username/password + BCrypt check, no
 * Spring UserDetailsService/AuthenticationManager involved) - without this
 * exclusion Spring Boot still spins up an unused default in-memory user and
 * logs a generated password on every boot.
 *
 * EnableScheduling powers MetricFetchService's stale-fetch-request sweep.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class MonitoringPocApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoringPocApplication.class, args);
    }
}
