package ai.zamu.whatsapp;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ai.zamu.conversations.Channel;
import ai.zamu.conversations.InboundMessage;
import ai.zamu.conversations.MessageStatus;
import ai.zamu.conversations.MessageType;
import ai.zamu.shared.ids.TenantId;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class InboundMappingTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("01920000-0000-7000-8000-000000000001"));
    private static final Instant NOW = Instant.parse("2026-10-14T06:00:00Z");
    private final JsonMapper json = JsonMapper.builder().build();

    private InboundMessage map(String messageJson) {
        JsonNode value = json.readTree("""
                {"contacts":[{"wa_id":"254712345678","profile":{"name":"Wanjiku"}}],"messages":[%s]}
                """.formatted(messageJson));
        return InboundMessageHandler.toInbound(TENANT, value, value.path("messages").path(0), NOW);
    }

    @Test
    void textMessagesCarryTheBodyNameAndMetaTimestamp() {
        InboundMessage m = map("""
                {"from":"254712345678","id":"wamid.1","timestamp":"1792000000","type":"text","text":{"body":"Habari"}}""");
        assertThat(m.channel()).isEqualTo(Channel.WHATSAPP);
        assertThat(m.type()).isEqualTo(MessageType.TEXT);
        assertThat(m.text()).isEqualTo("Habari");
        assertThat(m.profileName()).isEqualTo("Wanjiku");
        assertThat(m.externalId()).isEqualTo("wamid.1");
        assertThat(m.receivedAt()).isEqualTo(Instant.ofEpochSecond(1792000000L));
    }

    @Test
    void buttonAndListRepliesAreReadAsTheirTitle() {
        assertThat(map("""
                {"from":"254712345678","id":"wamid.2","type":"interactive",
                 "interactive":{"type":"button_reply","button_reply":{"id":"b1","title":"Yes"}}}""").text()).isEqualTo("Yes");
        assertThat(map("""
                {"from":"254712345678","id":"wamid.3","type":"interactive",
                 "interactive":{"type":"list_reply","list_reply":{"id":"l1","title":"Tuesday 10am"}}}""").text())
                .isEqualTo("Tuesday 10am");
        InboundMessage quick = map("""
                {"from":"254712345678","id":"wamid.4","type":"button","button":{"text":"Confirm","payload":"x"}}""");
        assertThat(quick.type()).isEqualTo(MessageType.INTERACTIVE);
        assertThat(quick.text()).isEqualTo("Confirm");
    }

    @Test
    void MSG_13_everythingElseIsMediaWithAReference() {
        InboundMessage image = map("""
                {"from":"254712345678","id":"wamid.5","type":"image","image":{"id":"9988","mime_type":"image/jpeg"}}""");
        assertThat(image.type()).isEqualTo(MessageType.MEDIA);
        assertThat(image.text()).isNull();
        assertThat(image.mediaRef()).isEqualTo("image:9988");
        assertThat(image.receivedAt()).as("no timestamp: falls back to now").isEqualTo(NOW);

        assertThat(map("""
                {"from":"254712345678","id":"wamid.6","type":"location","location":{"latitude":-1.28}}""").mediaRef())
                .isEqualTo("location");
    }

    @Test
    void MSG_15_metaStatusesMapToMessageStatuses() {
        assertThat(StatusUpdateHandler.map("sent")).isEqualTo(MessageStatus.SENT);
        assertThat(StatusUpdateHandler.map("delivered")).isEqualTo(MessageStatus.DELIVERED);
        assertThat(StatusUpdateHandler.map("read")).isEqualTo(MessageStatus.READ);
        assertThat(StatusUpdateHandler.map("failed")).isEqualTo(MessageStatus.FAILED);
        assertThat(StatusUpdateHandler.map("deleted")).isNull();
    }

    @Test
    void MSG_05_customerLockKeyFitsTheJobsColumn() {
        String key = WhatsAppWebhookService.customerLockKey(TENANT, "254712345678");
        assertThat(key).startsWith("cust:" + TENANT.value()).endsWith(":254712345678");
        assertThat(WhatsAppWebhookService.customerLockKey(TENANT, "1".repeat(40))).hasSizeLessThanOrEqualTo(64);
    }

    @Test
    void templatePlaceholdersAreCountedAndMustBeInOrder() {
        assertThat(WaTemplate.parameterCount("Hello {{1}}, see you {{2}}")).isEqualTo(2);
        assertThat(WaTemplate.parameterCount("No values")).isZero();
        assertThat(WaTemplate.placeholdersInOrder("Hello {{1}} and {{2}}")).isTrue();
        assertThat(WaTemplate.placeholdersInOrder("Hello {{2}}")).isFalse();
    }
}
