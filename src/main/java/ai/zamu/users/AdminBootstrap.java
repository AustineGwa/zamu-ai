package ai.zamu.users;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * ASSUMPTION: the spec doesn't say how the first Zamu admin is created. On startup, if
 * ZAMU_BOOTSTRAP_ADMIN_EMAIL is set and there is no admin yet, one is created; they then log in with an email
 * code and set up their authenticator app.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private final LoginService users;
    private final String email;
    private final String name;

    AdminBootstrap(LoginService users, @Value("${zamu.bootstrap.admin-email:}") String email,
            @Value("${zamu.bootstrap.admin-name:Zamu Admin}") String name) {
        this.users = users;
        this.email = email;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email != null && !email.isBlank()) {
            users.ensureBootstrapAdmin(email, name);
        }
    }
}
