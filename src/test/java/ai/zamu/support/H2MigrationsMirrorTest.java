package ai.zamu.support;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every MySQL migration needs an H2 twin with the same file name, or the local h2 profile drifts from the real
 * schema. Write the MySQL version first (it is the source of truth), then its H2 twin.
 */
class H2MigrationsMirrorTest {

    @Test
    void everyMySqlMigrationHasAnH2TwinAndViceVersa() throws IOException {
        assertThat(names("classpath:db/migration-h2/*.sql")).isEqualTo(names("classpath:db/migration/*.sql"));
    }

    private static Set<String> names(String pattern) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(pattern);
        return Arrays.stream(resources).map(Resource::getFilename).collect(Collectors.toCollection(TreeSet::new));
    }
}
