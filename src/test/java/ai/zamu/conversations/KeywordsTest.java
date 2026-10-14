package ai.zamu.conversations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordsTest {

    @Test
    void MSG_18_personOrMtuInAShortMessageAsksForStaff() {
        assertThat(Keywords.asksForPerson("person")).isTrue();
        assertThat(Keywords.asksForPerson("Mtu!")).isTrue();
        assertThat(Keywords.asksForPerson("nataka kuongea na mtu")).isTrue();
        assertThat(Keywords.asksForPerson("talk to a human please")).isTrue();
        assertThat(Keywords.asksForPerson("what time do you open")).isFalse();
        assertThat(Keywords.asksForPerson("is there a person who can tell me the price of a full service today")).isFalse();
    }

    @Test
    void MSG_19_onlyTheWordStopOnItsOwnOptsOut() {
        assertThat(Keywords.isStop("STOP")).isTrue();
        assertThat(Keywords.isStop(" stop. ")).isTrue();
        assertThat(Keywords.isStop("acha")).isTrue();
        assertThat(Keywords.isStop("please stop the reminder for tomorrow")).isFalse();
    }

    @Test
    void MSG_10_greetingsAloneAreRecognised() {
        assertThat(Keywords.isGreetingOnly("Hi")).isTrue();
        assertThat(Keywords.isGreetingOnly("habari za asubuhi")).isTrue();
        assertThat(Keywords.isGreetingOnly("hi, how much is a cleaning")).isFalse();
    }

    @Test
    void AI_02_swahiliIsGuessedFromCommonWords() {
        assertThat(LanguageGuess.guess("Habari, mnafungua saa ngapi leo?")).isEqualTo("sw");
        assertThat(LanguageGuess.guess("Mambo")).isEqualTo("sw");
        assertThat(LanguageGuess.guess("What time do you open today?")).isEqualTo("en");
        assertThat(LanguageGuess.guess("")).isNull();
    }
}
