package ai.zamu.handovers;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ai.zamu.bookings.BusinessHoursService;
import ai.zamu.bookings.DayHours;
import ai.zamu.conversations.Channel;
import ai.zamu.conversations.ConversationService;
import ai.zamu.conversations.InboundMessage;
import ai.zamu.conversations.MessageType;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.TenantFixture;
import ai.zamu.tenants.TenantService;
import ai.zamu.users.UserService;

import static org.assertj.core.api.Assertions.assertThat;

/** Handovers on MySQL (spec: Handovers HO-01 to HO-07). Messages enter through the channel-neutral engine. */
class HandoverFlowIT extends AbstractMySqlIT {

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    @Autowired
    ConversationService conversations;

    @Autowired
    HandoverService handovers;

    @Autowired
    HandoverEscalator escalator;

    @Autowired
    BusinessHoursService hours;

    TenantFixture fx;
    int sequence;

    @BeforeEach
    void setUp() {
        fx = TenantFixture.create(tenants, users);
    }

    @Test
    void HO_06_ownersAreEmailedOnceWhenNobodyAcceptsWithinThirtyMinutes() {
        receive(fx.idA(), "person");
        clock.advance(Duration.ofMinutes(29));
        assertThat(escalator.escalateDue()).isZero();

        clock.advance(Duration.ofMinutes(2));
        assertThat(escalator.escalateDue()).isEqualTo(1);
        assertThat(emails.sentTo("owner@afya.test")).singleElement()
                .satisfies(m -> assertThat(m.subject()).isEqualTo("A customer is waiting for a reply on Zamu"));
        assertThat(emails.sentTo("owner@baraka.test")).isEmpty();
        assertThat(emails.sentTo("staff@afya.test")).isEmpty();

        clock.advance(Duration.ofMinutes(30));
        assertThat(escalator.escalateDue()).as("escalated once only").isZero();
    }

    @Test
    void HO_06_anAcceptedHandoverIsNotEscalated() {
        UUID conversationId = receive(fx.idA(), "person");
        handovers.accept(fx.idA(), conversationId, fx.staffA().id());
        clock.advance(Duration.ofHours(1));

        assertThat(escalator.escalateDue()).isZero();
        assertThat(emails.sent()).isEmpty();
    }

    @Test
    void HO_06_noEscalationOutsideHandoverHours() {
        hours.saveWeek(fx.idA(), weekWithHandoverHours(LocalTime.of(14, 0), LocalTime.of(17, 0)));
        receive(fx.idA(), "person"); // 09:00 Nairobi, before handover hours
        clock.advance(Duration.ofMinutes(45));

        assertThat(escalator.escalateDue()).isZero();
    }

    @Test
    void HO_02_outsideHandoverHoursTheCustomerIsToldWhenStaffAreBack() {
        hours.saveWeek(fx.idA(), weekWithHandoverHours(LocalTime.of(14, 0), LocalTime.of(17, 0)));
        receive(fx.idA(), "person");

        assertThat(outbound(fx.idA())).last().asString()
                .isEqualTo("Our team is away right now and will reply by 2:00pm today. Leave your message here and we'll get back to you.");
    }

    @Test
    void HO_05_releasingHandsTheChatBackToZamu() {
        UUID conversationId = receive(fx.idA(), "person");
        handovers.accept(fx.idA(), conversationId, fx.staffA().id());
        handovers.release(fx.idA(), conversationId);

        assertThat(status(conversationId)).isEqualTo("bot");
        assertThat(handovers.openCount(fx.idA())).isZero();

        int before = outbound(fx.idA()).size();
        receive(fx.idA(), "What time do you open?");
        assertThat(outbound(fx.idA())).as("Zamu answers again").hasSizeGreaterThan(before);
    }

    @Test
    void HO_07_aStaffReplyTakesOverTheChatAndRecordsTheFirstReply() {
        UUID conversationId = receive(fx.idA(), "Hi");
        conversations.staffReply(fx.idA(), conversationId, fx.ownerA().id(), "Hello, this is Amina.");

        assertThat(status(conversationId)).isEqualTo("handed_over");
        assertThat(handovers.current(fx.idA(), conversationId)).hasValueSatisfying(h ->
                assertThat(h.acceptedBy()).isEqualTo(fx.ownerA().id()));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM handovers WHERE first_staff_reply_at IS NOT NULL")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void NFR_09_handoversOfAnotherTenantCannotBeAccepted() {
        UUID conversationOfB = receive(fx.idB(), "person");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        handovers.accept(fx.idA(), conversationOfB, fx.staffA().id()))
                .isInstanceOf(ai.zamu.shared.web.NotFoundException.class);
        assertThat(handovers.openCount(fx.idB())).isEqualTo(1);
    }

    private UUID receive(TenantId tenantId, String text) {
        sequence++;
        conversations.receive(new InboundMessage(Channel.WHATSAPP, tenantId, "254712345678", "Wanjiku",
                "wamid.test." + sequence, MessageType.TEXT, text, null, clock.instant()));
        return jdbc.sql("SELECT id FROM conversations WHERE tenant_id = ? ORDER BY id DESC LIMIT 1")
                .param(tenantId.bytes()).query((rs, n) -> Uuids.read(rs, "id")).single();
    }

    private List<String> outbound(TenantId tenantId) {
        return jdbc.sql("SELECT body FROM messages WHERE tenant_id = ? AND direction = 'out' ORDER BY id")
                .param(tenantId.bytes()).query(String.class).list();
    }

    private String status(UUID conversationId) {
        return jdbc.sql("SELECT status FROM conversations WHERE id = ?").param(Uuids.toBytes(conversationId))
                .query(String.class).single();
    }

    private static List<DayHours> weekWithHandoverHours(LocalTime from, LocalTime to) {
        List<DayHours> week = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            week.add(new DayHours(day, LocalTime.of(8, 0), LocalTime.of(18, 0), from, to));
        }
        return week;
    }
}
