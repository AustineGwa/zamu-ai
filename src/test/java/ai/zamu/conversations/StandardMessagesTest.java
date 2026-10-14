package ai.zamu.conversations;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandardMessagesTest {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 14);

    @Test
    void MSG_10_greetingDisclosesTheAssistantInTheCustomersLanguage() {
        assertThat(StandardMessages.greeting("en", "Afya Clinic"))
                .startsWith("Hello, welcome to Afya Clinic.").contains("automated assistant").contains("\"person\"");
        assertThat(StandardMessages.greeting("sw", "Afya Clinic"))
                .startsWith("Habari, karibu Afya Clinic.").contains("\"mtu\"");
    }

    @Test
    void HO_02_afterHoursMessageSaysWhenStaffAreBack() {
        ZonedDateTime tomorrow8 = ZonedDateTime.of(2026, 10, 15, 8, 0, 0, 0, NAIROBI);
        assertThat(StandardMessages.afterHours("en", tomorrow8, WEDNESDAY))
                .contains("will reply by 8:00am tomorrow.");
        assertThat(StandardMessages.afterHours("sw", tomorrow8, WEDNESDAY))
                .contains("kufikia saa 2:00 asubuhi kesho.");

        ZonedDateTime monday2pm = ZonedDateTime.of(2026, 10, 19, 14, 30, 0, 0, NAIROBI);
        assertThat(StandardMessages.afterHours("en", monday2pm, WEDNESDAY)).contains("by 2:30pm on Monday.");
        assertThat(StandardMessages.afterHours("sw", monday2pm, WEDNESDAY)).contains("saa 8:30 mchana Jumatatu.");
    }

    @Test
    void HO_02_withNoHandoverHoursTheMessageGivesNoTime() {
        assertThat(StandardMessages.afterHours("en", null, WEDNESDAY))
                .isEqualTo("Our team is away right now and will reply as soon as they're back. Leave your message here and we'll get back to you.");
    }

    @Test
    void swahiliTimeCountsHoursFromSixInTheMorning() {
        assertThat(StandardMessages.swahiliTime(ZonedDateTime.of(2026, 10, 14, 7, 0, 0, 0, NAIROBI))).isEqualTo("saa 1:00 asubuhi");
        assertThat(StandardMessages.swahiliTime(ZonedDateTime.of(2026, 10, 14, 12, 0, 0, 0, NAIROBI))).isEqualTo("saa 6:00 mchana");
        assertThat(StandardMessages.swahiliTime(ZonedDateTime.of(2026, 10, 14, 18, 15, 0, 0, NAIROBI))).isEqualTo("saa 12:15 jioni");
        assertThat(StandardMessages.swahiliTime(ZonedDateTime.of(2026, 10, 14, 20, 0, 0, 0, NAIROBI))).isEqualTo("saa 2:00 usiku");
    }
}
