package ai.zamu.shared.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeHasherTest {

    private final CodeHasher hasher = new CodeHasher("ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=");

    @Test
    void matchesOnlyTheSameCode() {
        byte[] hash = hasher.hash("123456");
        assertThat(hash).hasSize(32);
        assertThat(hasher.matches("123456", hash)).isTrue();
        assertThat(hasher.matches("123457", hash)).isFalse();
        assertThat(hasher.matches("123456", null)).isFalse();
    }

    @Test
    void differentKeyGivesDifferentHash() {
        CodeHasher other = new CodeHasher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        assertThat(other.hash("123456")).isNotEqualTo(hasher.hash("123456"));
    }
}
