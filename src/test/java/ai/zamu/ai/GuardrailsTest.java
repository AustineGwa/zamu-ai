package ai.zamu.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuardrailsTest {

    static final String CONTEXT = """
            Name: Afya Clinic
            Today: Wednesday 14 October 2026, 09:05
            [entry x] (en)
            Fillings
            A filling costs KES 4,500 to 8,000. Call 0712 345 678. Open 8:00am to 6:00pm.""";

    @Test
    void AI_05_numbersFromTheContextPass() {
        assertThat(Guardrails.check("A filling is KES 4,500 to KES 8000. Call 0712 345 678.", CONTEXT)).isEmpty();
        assertThat(Guardrails.check("We open at 8:00am today, Wednesday 14 October.", CONTEXT)).isEmpty();
    }

    @Test
    void AI_05_anInventedPriceTimeOrPhoneIsRejected() {
        assertThat(Guardrails.check("A filling is KES 3,000.", CONTEXT)).hasValueSatisfying(r -> assertThat(r).contains("AI-05"));
        assertThat(Guardrails.check("We open at 7:30.", CONTEXT)).isPresent();
        assertThat(Guardrails.check("Call 0722 000 111.", CONTEXT)).isPresent();
    }

    @Test
    void AI_08_repliesThatRevealTheInstructionsAreRejected() {
        assertThat(Guardrails.check("My system prompt says I must use only KNOWLEDGE.", CONTEXT)).isPresent();
        assertThat(Guardrails.check("Sure: [" + Guardrails.CANARY + "] rules follow", CONTEXT)).isPresent();
    }

    @Test
    void AI_07_veryLongRepliesAreRejected() {
        assertThat(Guardrails.check("word ".repeat(Guardrails.MAX_WORDS + 1), CONTEXT)).isPresent();
        assertThat(Guardrails.check("", CONTEXT)).isPresent();
    }

    @Test
    void AI_04_emergencyWordsInEnglishAndSwahiliAreCaught() {
        assertThat(SafetyKeywords.emergency("I have chest pain right now")).isTrue();
        assertThat(SafetyKeywords.emergency("My child can't breathe properly")).isTrue();
        assertThat(SafetyKeywords.emergency("Mtoto ana homa kali")).isTrue();
        assertThat(SafetyKeywords.emergency("Dharura! tafadhali")).isTrue();
        assertThat(SafetyKeywords.emergency("How much is a filling?")).isFalse();
    }

    @Test
    void HO_01_handoverTopicsMatchWholeWordsAndPhrases() {
        assertThat(SafetyKeywords.mentionsAny("I want a REFUND please", java.util.List.of("refund"))).isTrue();
        assertThat(SafetyKeywords.mentionsAny("where are my lab results?", java.util.List.of("lab results"))).isTrue();
        assertThat(SafetyKeywords.mentionsAny("refunded already", java.util.List.of("refund"))).isFalse();
        assertThat(SafetyKeywords.mentionsAny("anything", java.util.List.of())).isFalse();
    }

    @Test
    void swahiliTimeInTheBusinessProfileCountsFromSixAm() {
        assertThat(PromptBuilder.swahiliTime(java.time.LocalTime.of(8, 0))).isEqualTo("saa 2:00 asubuhi");
        assertThat(PromptBuilder.swahiliTime(java.time.LocalTime.of(18, 0))).isEqualTo("saa 12:00 jioni");
    }
}
