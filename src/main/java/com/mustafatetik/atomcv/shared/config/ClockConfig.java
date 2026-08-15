package com.mustafatetik.atomcv.shared.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock, as a bean.
 *
 * <p>Anything that decides on today's date takes this rather than calling
 * {@code now()}: scoring is deterministic by design (Bolum 19.6), and a
 * function that reads a static clock cannot be tested for it.
 *
 * <p>UTC, deliberately. Every stored instant is UTC and a server whose zone
 * changes must not change what a profile scores. The one place a local day
 * boundary will matter is the quota counter, and that gets its own decision.
 */
@Configuration
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.system(ZoneOffset.UTC);
    }
}
