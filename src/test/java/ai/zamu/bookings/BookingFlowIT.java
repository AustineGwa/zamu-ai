package ai.zamu.bookings;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import ai.zamu.conversations.Channel;
import ai.zamu.conversations.ConversationService;
import ai.zamu.conversations.InboundMessage;
import ai.zamu.conversations.MessageType;
import ai.zamu.shared.jobs.JobDrain;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.TenantFixture;
import ai.zamu.tenants.TenantService;
import ai.zamu.users.UserService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booking by WhatsApp on MySQL, through the engine and the offline AI stand-in (spec: BK-03 to BK-12). The test
 * clock starts on Wednesday 14 October 2026 at 09:00 in Nairobi; the business opens 08:00 to 18:00.
 */
class BookingFlowIT extends AbstractMySqlIT {

    static final String PHONE = "254712345678";

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    @Autowired
    ConversationService conversations;

    @Autowired
    BusinessHoursService hours;

    @Autowired
    BookingSetupService setup;

    @Autowired
    BookingService bookings;

    @Autowired
    JobDrain jobs;

    @Autowired
    ObjectMapper objectMapper;

    TenantFixture fx;
    UUID consultation;
    UUID checkup;
    int sequence;

    @BeforeEach
    void setUp() {
        fx = TenantFixture.create(tenants, users);
        List<DayHours> week = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            week.add(d == DayOfWeek.SUNDAY ? DayHours.closed(d)
                    : new DayHours(d, LocalTime.of(8, 0), LocalTime.of(18, 0), LocalTime.of(8, 0), LocalTime.of(18, 0)));
        }
        hours.saveWeek(fx.idA(), week);
        // Customers may change bookings until 1 hour before (the default is 4), so today's 11:00 can be changed.
        setup.saveSettings(fx.idA(), new BookingSettings(120, 30, 60, false, 30));
        consultation = setup.createService(fx.idA(), new ServiceForm("Consultation", "Kumwona daktari", null, 30, null,
                null, BigDecimal.ZERO, 0, true, List.of()));
        checkup = setup.createService(fx.idA(), new ServiceForm("Dental check-up", null, null, 45, null, null,
                new BigDecimal("500"), 0, true, List.of()));
    }

    @Test
    void BK_03_askingToBookOffersTheServicesThenTheNearestFiveTimes() {
        receive("I want to book please", null);
        JsonNode services = lastOptions();
        assertThat(services.path("choices")).hasSize(2);

        receive("Consultation", services.path("choices").path(0).path("id").asString());
        JsonNode times = lastOptions();
        assertThat(times.path("choices")).hasSize(5);
        assertThat(times.path("choices").path(0).path("title").asString()).isEqualTo("Wed 14 Oct, 11:00am");
        assertThat(lastOutbound().body()).contains("1. Wed 14 Oct at 11:00am");
    }

    @Test
    void BK_06_BK_07_tappingATimeConfirmsAServiceWithoutDeposit() {
        receive("I want to book a consultation", null);
        String first = lastOptions().path("choices").path(0).path("id").asString();

        receive("Wed 14 Oct, 11:00am", first);

        Booking b = only();
        assertThat(b.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.startsAt()).isEqualTo(Instant.parse("2026-10-14T08:00:00Z"));
        assertThat(b.customerPhone()).isEqualTo(PHONE);
        assertThat(lastOutbound().body()).isEqualTo(
                "You're booked: Consultation on Wed 14 Oct at 11:00am. Reply 1 to reschedule or 2 to cancel.");
        assertThat(lastOptions().path("asButtons").asBoolean()).isTrue();
        assertThat(conversationStatus()).isEqualTo("bot");
    }

    @Test
    void BK_05_aTimeTakenMeanwhileOffersOtherTimes() {
        receive("I want to book a consultation", null);
        String first = lastOptions().path("choices").path(0).path("id").asString();
        bookings.hold(fx.idA(), new BookingService.HoldRequest(UUID.randomUUID(), "Other", "254700000001", null,
                consultation, null, Instant.parse("2026-10-14T08:00:00Z"), "staff", null));

        receive("Wed 14 Oct, 11:00am", first);

        assertThat(lastOutbound().body()).startsWith("Sorry, that time was just taken.");
        assertThat(lastOptions().path("choices").path(0).path("title").asString()).isEqualTo("Wed 14 Oct, 11:30am");
    }

    @Test
    void BK_06_aDepositServiceWaitsForPaymentAndGoesToStaff() {
        receive("I want to book a dental check-up", null);
        receive("Wed", lastOptions().path("choices").path(0).path("id").asString());

        assertThat(only().status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(outbound()).anySatisfy(m -> assertThat(m).startsWith("A deposit of KES 500 secures your booking."));
        assertThat(conversationStatus()).isEqualTo("handed_over");
    }

    @Test
    void BK_08_typingTwoAfterTheConfirmationCancelsAfterConfirming() {
        bookFirstConsultation();

        receive("2", null);
        assertThat(lastOutbound().body()).startsWith("Cancel your Consultation on Wed 14 Oct at 11:00am?");
        receive("Cancel", lastOptions().path("choices").path(0).path("id").asString());

        assertThat(only().status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(lastOutbound().body()).isEqualTo("Your booking is cancelled. You're welcome to book again any time.");
    }

    @Test
    void BK_08_rescheduleMovesTheBookingToTheChosenTime() {
        bookFirstConsultation();

        receive("1", null);
        JsonNode times = lastOptions();
        String later = times.path("choices").path(2).path("id").asString();
        receive("later", later);

        assertThat(only().startsAt()).isEqualTo(Instant.parse("2026-10-14T09:00:00Z"));
        assertThat(lastOutbound().body()).startsWith("You're booked: Consultation on Wed 14 Oct at 12:00pm.");
    }

    @Test
    void BK_08_afterTheCutOffChangesGoToStaff() {
        bookFirstConsultation(); // 11:00 today; the cut-off is 1 hour before
        clock.advance(Duration.ofMinutes(90));

        receive("I need to cancel my booking", null);

        assertThat(only().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(outbound()).anySatisfy(m -> assertThat(m).isEqualTo("This booking is too soon to change here."));
        assertThat(conversationStatus()).isEqualTo("handed_over");
    }

    @Test
    void NFR_09_aBookingOptionForAnotherCustomersBookingIsIgnored() {
        Booking other = bookings.hold(fx.idA(), new BookingService.HoldRequest(UUID.randomUUID(), "Other",
                "254700000001", null, consultation, null, Instant.parse("2026-10-15T08:00:00Z"), "staff", null));

        receive("Cancel", "bk:c:" + other.id());

        assertThat(bookings.get(fx.idA(), other.id()).status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void BK_11_BK_12_aReminderGoesOutTheDayBeforeAndOneReplyConfirms() {
        receive("I want to book a consultation", null);
        clock.set(Instant.parse("2026-10-14T06:00:00Z"));
        // Thursday 15 October, 11:00: more than 3 hours away, so a 24-hour reminder is scheduled.
        Booking b = bookings.hold(fx.idA(), new BookingService.HoldRequest(customerId(), "Wanjiku", PHONE, null,
                consultation, null, Instant.parse("2026-10-15T08:00:00Z"), "bot", null));
        jobs.drain();
        int before = outbound().size();

        clock.advance(Duration.ofHours(2)); // 24 hours before is 08:00Z on the 14th
        jobs.drain();

        assertThat(outbound()).hasSize(before + 1);
        assertThat(lastOutbound().body()).startsWith("Reminder: your appointment at Afya Clinic is tomorrow, Thu 15 Oct");
        receive("1", null);
        assertThat(lastOutbound().body()).isEqualTo("Thank you, see you then!");
        assertThat(bookings.get(fx.idA(), b.id()).awaitingReply()).isNull();
    }

    @Test
    void BK_12_noReminderForABookingMadeLessThanThreeHoursAhead() {
        receive("Hi", null);
        bookings.hold(fx.idA(), new BookingService.HoldRequest(customerId(), "Wanjiku", PHONE, null, consultation,
                null, Instant.parse("2026-10-14T08:30:00Z"), "bot", null));

        assertThat(jdbc.sql("SELECT COUNT(*) FROM jobs WHERE type = 'booking_reminder'").query(Integer.class).single())
                .isZero();
    }

    @Test
    void BK_09_staffCancellingTellsTheCustomer() {
        bookFirstConsultation();

        bookings.cancel(fx.idA(), only().id(), "staff");

        assertThat(lastOutbound().body()).startsWith("Sorry, your Consultation on Wed 14 Oct at 11:00am has been cancelled.");
    }

    @Test
    void BK_02_holdsThatExpireReleaseTheSlot() {
        receive("I want to book a dental check-up", null);
        receive("Wed", lastOptions().path("choices").path(0).path("id").asString());
        clock.advance(Duration.ofHours(3));

        assertThat(bookings.expireHolds()).isEqualTo(1);
        assertThat(only().status()).isEqualTo(BookingStatus.CANCELLED);
    }

    private void bookFirstConsultation() {
        receive("I want to book a consultation", null);
        receive("Wed 14 Oct, 11:00am", lastOptions().path("choices").path(0).path("id").asString());
        assertThat(only().status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    private void receive(String text, String replyId) {
        sequence++;
        conversations.receive(new InboundMessage(Channel.WHATSAPP, fx.idA(), PHONE, "Wanjiku", "wamid.bk." + sequence,
                replyId == null ? MessageType.TEXT : MessageType.INTERACTIVE, text, null, clock.instant(), replyId));
    }

    record Out(String type, String body, String json) {
    }

    private Out lastOutbound() {
        return jdbc.sql("SELECT type, body, template_json FROM messages WHERE tenant_id = ? AND direction = 'out'"
                        + " ORDER BY created_at DESC, id DESC LIMIT 1")
                .param(fx.idA().bytes())
                .query((rs, n) -> new Out(rs.getString("type"), rs.getString("body"), rs.getString("template_json")))
                .single();
    }

    private JsonNode lastOptions() {
        Out out = lastOutbound();
        assertThat(out.type()).isEqualTo("interactive");
        return objectMapper.readTree(out.json());
    }

    private List<String> outbound() {
        return jdbc.sql("SELECT body FROM messages WHERE tenant_id = ? AND direction = 'out' ORDER BY created_at, id")
                .param(fx.idA().bytes()).query(String.class).list();
    }

    private Booking only() {
        List<Booking> all = bookings.between(fx.idA(), Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-12-31T00:00:00Z"), true).stream()
                .filter(b -> PHONE.equals(b.customerPhone())).toList();
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    private UUID customerId() {
        return jdbc.sql("SELECT id FROM customers WHERE tenant_id = ? AND phone = ?")
                .param(fx.idA().bytes()).param(PHONE)
                .query((rs, n) -> ai.zamu.shared.ids.Uuids.read(rs, "id")).single();
    }

    private String conversationStatus() {
        return jdbc.sql("SELECT status FROM conversations WHERE tenant_id = ? ORDER BY id DESC LIMIT 1")
                .param(fx.idA().bytes()).query(String.class).single();
    }
}
