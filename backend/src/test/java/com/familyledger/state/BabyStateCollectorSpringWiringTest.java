package com.familyledger.state;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class BabyStateCollectorSpringWiringTest {

    @Test
    void createsCollectorFromConfiguredDailyLogDirectory() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PropertySourcesPlaceholderConfigurer.class, () -> {
                PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
                Properties properties = new Properties();
                properties.setProperty("family-state.baby.daily-log-directory", "/tmp/baby-daily-logs");
                configurer.setProperties(properties);
                return configurer;
            });
            context.registerBean(BabyStateCollector.class);
            context.refresh();

            assertThat(context.getBean(BabyStateCollector.class)).isNotNull();
        }
    }
}
