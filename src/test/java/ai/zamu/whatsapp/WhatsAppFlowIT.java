package ai.zamu.whatsapp;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ai.zamu.conversations.ConversationService;
import ai.zamu.conversations.WindowClosedException;
import ai.zamu.handovers.HandoverService;
import ai.zamu.reports.DailyUsage;
import ai.zamu.reports.UsageService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.jobs.JobDrain;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.TenantFixture;
import ai.zamu.tenants.TenantService;
import ai.zamu.users.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WhatsApp end to end on MySQL: signed webhook, routing, jobs, the conversation engine and sending through a mocked
 * Cloud API (spec: Messaging and the conversation engine, MSG-01 to MSG-19; handovers HO-01, HO-07).
 */
@AutoConfigureMockMvc
class WhatsAppFlowIT extends AbstractMySqlIT {

    static final String APP_SECRET = "test-app-secret";
    static final String PHONE_ID_A = "1110000001";
    static final String PHONE_ID_B = "2220000002";
    static final String WABA_A = "9990000001";
    static final String CUSTOMER = "254712345678";

    @Autowired
    MockMvc mvc;

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    @Autowired
    WhatsAppAccountService accounts;

    @Autowired
    TemplateService templates;

    @Autowired
    ConversationService conversations;

    @Autowired
    HandoverService handovers;

    @Autowired
    UsageService usage;

    @Autowired
    JobDrain jobs;

    @MockitoBean
    WhatsAppApi api;

    TenantFixture fx;
    final AtomicInteger sent = new AtomicInteger();

    @BeforeEach
    void setUp() {
        fx = TenantFixture.create(tenants, users);
        accounts.connect(fx.idA(), new WhatsAppConnectForm(WABA_A, PHONE_ID_A, "+254 700 000 001", "token-a"));
        accounts.connect(fx.idB(), new WhatsAppConnectForm("9990000002", PHONE_ID_B, "+254 700 000 002", "token-b"));
        when(api.sendText(any(), anyString(), anyString())).thenAnswer(i -> "wamid.out." + sent.incrementAndGet());
        when(api.sendTemplate(any(), anyString(), anyString(), anyString(), anyList()))
                .thenAnswer(i -> "wamid.out." + sent.incrementAndGet());
    }

    // ---- MSG-01 webhook authentication -------------------------------------------------------------

    @Test
    void MSG_01_webhooksWithoutAValidSignatureAreRejectedAndNotStored() throws Exception {
        byte[] body = messagesWebhook(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "Hello")).getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(WebhookSignature.HEADER, WebhookSignature.header("wrong-secret", body)))
                .andExpect(status().isUnauthorized());

        assertThat(count("SELECT COUNT(*) FROM webhook_events")).isZero();
        assertThat(count("SELECT COUNT(*) FROM jobs")).isZero();
    }

    @Test
    void MSG_01_subscriptionHandshakeEchoesTheChallengeOnlyForTheRightToken() throws Exception {
        mvc.perform(get("/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "test-verify-token")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isOk()).andExpect(content().string("1158201444"));
        mvc.perform(get("/webhooks/whatsapp").param("hub.mode", "subscribe").param("hub.verify_token", "guess")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isForbidden());
    }

    // ---- Routing and de-duplication -----------------------------------------------------------------

    @Test
    void MSG_03_messagesAreRoutedByPhoneNumberIdAndUnknownNumbersAreOnlyStored() throws Exception {
        deliver(PHONE_ID_B, textMessage("wamid.in.b1", CUSTOMER, "Hello"));
        deliver("5550000000", textMessage("wamid.in.x1", CUSTOMER, "Hello"));
        jobs.drain();

        assertThat(count("SELECT COUNT(*) FROM messages WHERE tenant_id = ?", fx.idB().bytes())).isPositive();
        assertThat(count("SELECT COUNT(*) FROM messages WHERE tenant_id = ?", fx.idA().bytes())).isZero();
        assertThat(count("SELECT COUNT(*) FROM webhook_events WHERE tenant_id IS NULL")).isEqualTo(1);
        assertThat(accounts.view(fx.idB()).orElseThrow().webhookVerified()).isTrue();
    }

    @Test
    void MSG_02_aRedeliveredMessageIsProcessedOnce() throws Exception {
        String message = textMessage("wamid.in.dup", CUSTOMER, "What time do you open?");
        deliver(PHONE_ID_A, message);
        deliver(PHONE_ID_A, message); // Meta retries before the first is processed
        jobs.drain();
        deliver(PHONE_ID_A, message); // and after
        jobs.drain();

        assertThat(count("SELECT COUNT(*) FROM messages WHERE wa_message_id = 'wamid.in.dup'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM messages WHERE direction = 'in'")).isEqualTo(1);
    }

    // ---- The engine ---------------------------------------------------------------------------------

    @Test
    void MSG_10_AI_03_aNewCustomerIsGreetedAndAnUnansweredQuestionGoesToStaff() throws Exception {
        deliver(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "How much is a filling?"));
        jobs.drain();

        assertThat(outbound(fx.idA())).containsExactly(
                "Hello, welcome to Afya Clinic. I'm the automated assistant and I can answer questions. Type \"person\" at any time to talk to our team.",
                "I'm not sure about that, let me connect you to our team.");
        verify(api).markReadWithTyping(any(), eq("wamid.in.1")); // MSG-06
        verify(api, org.mockito.Mockito.times(2)).sendText(any(), eq(CUSTOMER), anyString());
        assertThat(count("SELECT COUNT(*) FROM messages WHERE direction = 'out' AND status = 'sent'")).isEqualTo(2);
        assertThat(handovers.openCount(fx.idA())).isEqualTo(1);
        assertThat(conversationStatus()).isEqualTo("handed_over");
    }

    @Test
    void MSG_08_whileStaffHaveTheChatZamuStaysQuiet() throws Exception {
        deliver(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "person"));
        jobs.drain();
        int before = outbound(fx.idA()).size();

        deliver(PHONE_ID_A, textMessage("wamid.in.2", CUSTOMER, "Hello? Anyone there?"));
        jobs.drain();

        assertThat(outbound(fx.idA())).hasSize(before);
        assertThat(count("SELECT COUNT(*) FROM messages WHERE direction = 'in'")).isEqualTo(2);
    }

    @Test
    void MSG_18_mtuHandsOverAndRepliesInSwahili() throws Exception {
        deliver(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "Nataka kuongea na mtu"));
        jobs.drain();

        List<String> out = outbound(fx.idA());
        assertThat(out).hasSize(2);
        assertThat(out.get(0)).startsWith("Habari, karibu Afya Clinic.");
        assertThat(out.get(1)).isEqualTo("Ninakuunganisha na timu yetu sasa. Mtu atakujibu hivi punde.");
        assertThat(handovers.openCount(fx.idA())).isEqualTo(1);
    }

    @Test
    void MSG_09_inStaffOnlyModeEachChatGetsTheHoldingReplyOnce() throws Exception {
        jdbc.sql("UPDATE tenants SET status = 'live' WHERE id = ?").param(fx.idA().bytes()).update();
        tenants.setStaffOnly(fx.idA(), true);

        deliver(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "Hello"));
        deliver(PHONE_ID_A, textMessage("wamid.in.2", CUSTOMER, "Are you open today?"));
        jobs.drain();

        assertThat(outbound(fx.idA())).containsExactly("Thanks for your message. Our team will reply here shortly.");
        assertThat(conversationStatus()).isEqualTo("handed_over");
    }

    @Test
    void MSG_13_photosGetTheTextOnlyReply() throws Exception {
        deliver(PHONE_ID_A, """
                {"from":"%s","id":"wamid.in.img","timestamp":"1791957600","type":"image","image":{"id":"777","mime_type":"image/jpeg"}}"""
                .formatted(CUSTOMER));
        jobs.drain();

        assertThat(outbound(fx.idA())).last().asString().startsWith("Thanks! I can only read text messages for now.");
        assertThat(count("SELECT COUNT(*) FROM messages WHERE media_ref = 'image:777'")).isEqualTo(1);
    }

    @Test
    void MSG_19_stopOptsTheCustomerOut() throws Exception {
        deliver(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "STOP"));
        jobs.drain();

        assertThat(outbound(fx.idA())).last().asString().startsWith("Done. You won't get reminders or offers from us any more.");
        assertThat(count("SELECT COUNT(*) FROM customers WHERE opted_out_at IS NOT NULL")).isEqualTo(1);
    }

    // ---- Delivery ---------------------------------------------------------------------------------

    @Test
    void MSG_15_deliveryStatusesOnlyMoveForward() throws Exception {
        deliver(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "Hi"));
        jobs.drain();
        String greetingId = "wamid.out.1";

        deliverStatuses(statusUpdate(greetingId, "delivered"), statusUpdate(greetingId, "sent")); // out of order
        jobs.drain();
        assertThat(messageStatus(greetingId)).isEqualTo("delivered");

        deliverStatuses(statusUpdate(greetingId, "read"));
        jobs.drain();
        assertThat(messageStatus(greetingId)).isEqualTo("read");
    }

    @Test
    void MSG_15_aFailedStatusIsRecordedWithMetasErrorCode() throws Exception {
        deliver(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "Hi"));
        jobs.drain();

        deliverStatuses("""
                {"id":"wamid.out.1","status":"failed","timestamp":"1791957660","recipient_id":"%s","errors":[{"code":131026,"title":"x"}]}"""
                .formatted(CUSTOMER));
        jobs.drain();

        assertThat(messageStatus("wamid.out.1")).isEqualTo("failed");
        assertThat(jdbc.sql("SELECT error_code FROM messages WHERE wa_message_id = 'wamid.out.1'").query(String.class).single())
                .isEqualTo("131026");
    }

    @Test
    void NFR_04_aSendThatMetaRefusesForGoodIsMarkedFailedWithoutRetrying() throws Exception {
        doThrow(new WhatsAppApiException("131047", null)).when(api).sendText(any(), anyString(), anyString());

        deliver(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "Hi"));
        jobs.drain();

        assertThat(count("SELECT COUNT(*) FROM messages WHERE direction = 'out' AND status = 'failed' AND error_code = '131047'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM jobs WHERE type = 'send_message' AND status <> 'done'")).isZero();
    }

    // ---- Staff replies and the 24-hour window -------------------------------------------------------

    @Test
    void MSG_14_HO_07_afterTwentyFourHoursStaffCanSendOnlyApprovedTemplates() throws Exception {
        deliver(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "person"));
        jobs.drain();
        UUID conversationId = conversationId();
        UUID templateId = templates.create(fx.idA(), new WaTemplateForm("follow_up", "utility", "en", "Hello {{1}}, we tried to reach you."));
        templates.recordStatus(fx.idA(), templateId, WaTemplateStatus.APPROVED, null);

        clock.advance(Duration.ofHours(25));

        assertThatThrownBy(() -> conversations.staffReply(fx.idA(), conversationId, fx.staffA().id(), "Sorry for the wait"))
                .isInstanceOf(WindowClosedException.class);
        conversations.staffTemplate(fx.idA(), conversationId, fx.staffA().id(), "follow_up", "en", List.of("Wanjiku"));
        jobs.drain();

        verify(api).sendTemplate(any(), eq(CUSTOMER), eq("follow_up"), eq("en"), eq(List.of("Wanjiku")));
        assertThat(outbound(fx.idA())).last().isEqualTo("Hello Wanjiku, we tried to reach you.");
    }

    @Test
    void MSG_14_aQueuedFreeTextMessageIsNotSentOnceTheWindowHasClosed() throws Exception {
        deliver(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "person"));
        jobs.drain();
        conversations.staffReply(fx.idA(), conversationId(), fx.staffA().id(), "We'll call you back");
        // The reply is queued but not sent before the window closes (e.g. a long outage).
        clock.advance(Duration.ofHours(25));
        jobs.drain();

        verify(api, never()).sendText(any(), anyString(), eq("We'll call you back"));
        assertThat(count("SELECT COUNT(*) FROM messages WHERE direction = 'out' AND error_code = 'window_closed'")).isEqualTo(1);
    }

    // ---- Templates and usage ----------------------------------------------------------------------

    @Test
    void templateReviewResultsFromMetaUpdateTheTemplate() throws Exception {
        UUID id = templates.create(fx.idA(), new WaTemplateForm("reminder", "utility", "sw", "Habari {{1}}"));
        templates.submit(fx.idA(), id);
        verify(api).submitTemplate(any(), eq("reminder"), eq("utility"), eq("sw"), eq("Habari {{1}}"), eq(1));

        signedPost("""
                {"object":"whatsapp_business_account","entry":[{"id":"%s","time":1791957600,"changes":[{"field":"message_template_status_update",
                 "value":{"event":"APPROVED","message_template_id":12345,"message_template_name":"reminder","message_template_language":"sw","reason":"NONE"}}]}]}"""
                .formatted(WABA_A));

        assertThat(templates.list(fx.idA())).singleElement()
                .satisfies(t -> assertThat(t.status()).isEqualTo(WaTemplateStatus.APPROVED));
        assertThat(templates.approved(fx.idA())).extracting(ai.zamu.conversations.TemplateCatalog.TemplateOption::name).containsExactly("reminder");
        assertThat(templates.approved(fx.idB())).isEmpty();
    }

    @Test
    void MSG_17_usageCountsOneBillableConversationPerCustomerPerDay() throws Exception {
        deliver(PHONE_ID_A, textMessage("wamid.in.1", CUSTOMER, "Hi"));
        deliver(PHONE_ID_A, textMessage("wamid.in.2", CUSTOMER, "What time do you open?"));
        jobs.drain();

        DailyUsage day = usage.day(fx.idA(), LocalDate.of(2026, 10, 14)).orElseThrow();
        assertThat(day.conversations()).isEqualTo(1);
        assertThat(day.messagesIn()).isEqualTo(2);
        assertThat(day.messagesOut()).isEqualTo(outbound(fx.idA()).size());
        assertThat(usage.day(fx.idB(), LocalDate.of(2026, 10, 14))).isEmpty();
    }

    // ---- Helpers ------------------------------------------------------------------------------------

    static String textMessage(String id, String from, String text) {
        return """
                {"from":"%s","id":"%s","timestamp":"1791957600","type":"text","text":{"body":"%s"}}"""
                .formatted(from, id, text);
    }

    static String statusUpdate(String waMessageId, String status) {
        return """
                {"id":"%s","status":"%s","timestamp":"1791957660","recipient_id":"%s"}""".formatted(waMessageId, status, CUSTOMER);
    }

    static String messagesWebhook(String phoneNumberId, String message) {
        return """
                {"object":"whatsapp_business_account","entry":[{"id":"%s","changes":[{"field":"messages","value":{
                 "messaging_product":"whatsapp","metadata":{"display_phone_number":"254700000001","phone_number_id":"%s"},
                 "contacts":[{"profile":{"name":"Wanjiku"},"wa_id":"%s"}],"messages":[%s]}}]}]}"""
                .formatted(WABA_A, phoneNumberId, CUSTOMER, message);
    }

    void deliver(String phoneNumberId, String message) throws Exception {
        signedPost(messagesWebhook(phoneNumberId, message));
    }

    void deliverStatuses(String... statuses) throws Exception {
        signedPost("""
                {"object":"whatsapp_business_account","entry":[{"id":"%s","changes":[{"field":"messages","value":{
                 "messaging_product":"whatsapp","metadata":{"display_phone_number":"254700000001","phone_number_id":"%s"},
                 "statuses":[%s]}}]}]}""".formatted(WABA_A, PHONE_ID_A, String.join(",", statuses)));
    }

    void signedPost(String json) throws Exception {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhooks/whatsapp").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(WebhookSignature.HEADER, WebhookSignature.header(APP_SECRET, body)))
                .andExpect(status().isOk());
    }

    List<String> outbound(TenantId tenantId) {
        return jdbc.sql("SELECT body FROM messages WHERE tenant_id = ? AND direction = 'out' ORDER BY id")
                .param(tenantId.bytes()).query(String.class).list();
    }

    String messageStatus(String waMessageId) {
        return jdbc.sql("SELECT status FROM messages WHERE wa_message_id = ?").param(waMessageId).query(String.class).single();
    }

    String conversationStatus() {
        return jdbc.sql("SELECT status FROM conversations WHERE tenant_id = ?").param(fx.idA().bytes()).query(String.class).single();
    }

    UUID conversationId() {
        return jdbc.sql("SELECT id FROM conversations WHERE tenant_id = ?").param(fx.idA().bytes())
                .query((rs, n) -> Uuids.read(rs, "id")).single();
    }

    int count(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query(Integer.class).single();
    }
}
