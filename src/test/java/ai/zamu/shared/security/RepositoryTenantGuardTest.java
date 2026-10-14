package ai.zamu.shared.security;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import ai.zamu.shared.ids.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NFR-09 guard: every non-private method of every *Repository class (and the job queue) either takes a
 * {@link TenantId} or is annotated {@link CrossTenant} with a reason. A new repository method that forgets the
 * tenant fails the build here.
 */
class RepositoryTenantGuardTest {

    @Test
    void NFR_09_everyRepositoryMethodTakesTheTenantOrSaysWhyNot() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*(Repository|JobQueue)$")));

        List<String> violations = new ArrayList<>();
        int checked = 0;
        for (BeanDefinition candidate : scanner.findCandidateComponents("ai.zamu")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isPrivate(method.getModifiers()) || method.isSynthetic() || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                checked++;
                boolean takesTenant = Arrays.asList(method.getParameterTypes()).contains(TenantId.class);
                CrossTenant reason = method.getAnnotation(CrossTenant.class);
                if (!takesTenant && (reason == null || reason.value().isBlank())) {
                    violations.add(type.getSimpleName() + "." + method.getName());
                }
            }
        }
        assertThat(checked).as("repository methods found").isGreaterThan(10);
        assertThat(violations).as("methods without a TenantId parameter or @CrossTenant reason").isEmpty();
    }
}
