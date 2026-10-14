package ai.zamu.shared.phone;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KenyanPhoneTest {

    @Test
    void PAY_04_normalisesTheWaysPeopleTypeKenyanNumbers() {
        assertThat(KenyanPhone.normalise("0712 345 678")).hasValue("254712345678");
        assertThat(KenyanPhone.normalise("712345678")).hasValue("254712345678");
        assertThat(KenyanPhone.normalise("+254 712 345 678")).hasValue("254712345678");
        assertThat(KenyanPhone.normalise("254-712-345-678")).hasValue("254712345678");
        assertThat(KenyanPhone.normalise("0110 123 456")).hasValue("254110123456");
    }

    @Test
    void PAY_04_rejectsEverythingElse() {
        assertThat(KenyanPhone.normalise("0212345678")).isEmpty();
        assertThat(KenyanPhone.normalise("+44 7700 900123")).isEmpty();
        assertThat(KenyanPhone.normalise("07123")).isEmpty();
        assertThat(KenyanPhone.normalise("call me")).isEmpty();
        assertThat(KenyanPhone.normalise(null)).isEmpty();
    }

    @Test
    void displaysInTheLocalFormat() {
        assertThat(KenyanPhone.display("254712345678")).isEqualTo("0712 345 678");
    }
}
