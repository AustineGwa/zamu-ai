package ai.zamu.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class H2LocalProfileGuardTest {

    @Test
    void h2AloneOrMysqlAloneIsFine() {
        assertThatCode(() -> H2LocalProfileGuard.check(profiles("h2"))).doesNotThrowAnyException();
        assertThatCode(() -> H2LocalProfileGuard.check(profiles("mysql"))).doesNotThrowAnyException();
        assertThatCode(() -> H2LocalProfileGuard.check(profiles("mysql", "test"))).doesNotThrowAnyException();
    }

    @Test
    void noProfileSetFallsBackToTheH2Default() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("h2");
        assertThatCode(() -> H2LocalProfileGuard.check(environment)).doesNotThrowAnyException();
    }

    @Test
    void bothDatabaseProfilesTogetherAreRefused() {
        assertThatThrownBy(() -> H2LocalProfileGuard.check(profiles("h2", "mysql")))
                .hasMessageContaining("can't be active together");
    }

    @Test
    void aProfileWithoutADatabaseIsRefused() {
        assertThatThrownBy(() -> H2LocalProfileGuard.check(profiles("prod")))
                .hasMessageContaining("No database profile is active");
    }

    private static MockEnvironment profiles(String... active) {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("h2");
        environment.setActiveProfiles(active);
        return environment;
    }
}
