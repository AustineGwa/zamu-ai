package ai.zamu.shared.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingTest {

    @Test
    void NFR_13_masksPhoneNumbersLikeTheSpecExample() {
        assertThat(Masking.phone("254712345123")).isEqualTo("2547XXXXX123");
        assertThat(Masking.phone("+254 712 345 123")).isEqualTo("2547XXXXX123");
        assertThat(Masking.phone("12345")).isEqualTo("XXXXX");
        assertThat(Masking.phone(null)).isEmpty();
    }

    @Test
    void NFR_13_masksEmailAddresses() {
        assertThat(Masking.email("jane.doe@clinic.co.ke")).isEqualTo("j***@clinic.co.ke");
        assertThat(Masking.email("nonsense")).isEqualTo("***");
    }
}
