package ai.zamu.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base for integration tests: the whole application against real MySQL 8.4 in Testcontainers (spec: Testing,
 * "no H2 or in-memory substitutes"). One container is shared by every test class; tables are emptied before
 * each test and the clock is reset to {@link TestSupportConfig#START}.
 */
@SpringBootTest
@ActiveProfiles({"mysql", "test"})
@Import(TestSupportConfig.class)
public abstract class AbstractMySqlIT {

    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci",
                    "--default-time-zone=+00:00")
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true")
            .withUrlParam("sslMode", "DISABLED")
            .withUrlParam("allowPublicKeyRetrieval", "true");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected RecordingEmailSender emails;

    @BeforeEach
    void resetDatabaseAndClock() {
        for (String table : new String[] {"audit_logs", "login_codes", "jobs", "leads", "SPRING_SESSION_ATTRIBUTES",
                "SPRING_SESSION", "usage_daily", "handovers", "messages", "conversations", "customers", "wa_templates",
                "whatsapp_accounts", "business_hours", "webhook_events", "users", "tenants"}) {
            jdbc.sql("DELETE FROM " + table).update();
        }
        clock.set(TestSupportConfig.START);
        emails.clear();
    }
}
