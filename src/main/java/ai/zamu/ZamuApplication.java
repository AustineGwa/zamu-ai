package ai.zamu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Zamu: one Spring Boot application serving the dashboard, admin console, webhooks and job runners. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableCaching
public class ZamuApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZamuApplication.class, args);
    }
}
