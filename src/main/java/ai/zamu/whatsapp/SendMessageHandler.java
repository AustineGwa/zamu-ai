package ai.zamu.whatsapp;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ai.zamu.conversations.Channel;
import ai.zamu.conversations.ConversationService;
import ai.zamu.conversations.MessageStatus;
import ai.zamu.conversations.MessageType;
import ai.zamu.conversations.OutboundMessage;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.jobs.Job;
import ai.zamu.shared.jobs.JobHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * Job {@code send_message}: delivers one queued outbound message through the Cloud API, in order per conversation
 * (the job lock key). Free text only inside the 24-hour window; templates at any time (MSG-14).
 *
 * <p>Failures are retried with back-off (NFR-04) unless Meta's error code says retrying can't help; on the last
 * attempt the message is marked failed so staff see it in the inbox.
 * <p>Known gap: if Meta accepts the message but storing "sent" fails, the retry sends it again. Rare, and a
 * duplicate is better than a lost reply.
 */
@Component
class SendMessageHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(SendMessageHandler.class);

    /**
     * Meta error codes that won't succeed on retry. ASSUMPTION: from Meta's error code reference at the time of
     * writing (131047 re-engagement outside the window, 131026 undeliverable, 131051 unsupported type, 132000 and
     * 132001 template parameter or existence errors, 470 legacy window error, 190 token expired, 10 and 200
     * permission). Check the current list.
     */
    static final Set<String> PERMANENT = Set.of("131047", "131026", "131051", "132000", "132001", "470", "190", "10",
            "200", "http_400", "http_401", "http_403");

    private final ConversationService conversations;
    private final WhatsAppAccountService accounts;
    private final WhatsAppApi api;
    private final ObjectMapper objectMapper;

    SendMessageHandler(ConversationService conversations, WhatsAppAccountService accounts, WhatsAppApi api,
            ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.accounts = accounts;
        this.api = api;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return OutboundMessage.SEND_JOB;
    }

    @Override
    public void handle(Job job) {
        TenantId tenantId = job.tenantId();
        UUID messageId = UUID.fromString(objectMapper.readTree(job.payloadJson()).path("messageId").asString(""));
        OutboundMessage message = conversations.outboundForSend(tenantId, messageId).orElse(null);
        if (message == null || message.channel() != Channel.WHATSAPP || message.status() != MessageStatus.QUEUED) {
            return; // gone, another channel, or already sent (idempotent)
        }
        Credentials credentials = accounts.credentials(tenantId).orElse(null);
        if (credentials == null) {
            conversations.markFailed(tenantId, messageId, "not_connected");
            return;
        }
        boolean template = message.type() == MessageType.TEMPLATE && message.template() != null;
        if (!template && !message.windowOpen()) {
            conversations.markFailed(tenantId, messageId, "window_closed"); // MSG-14
            return;
        }
        try {
            String waMessageId;
            if (template) {
                waMessageId = api.sendTemplate(credentials, message.toPhone(), message.template().name(),
                        message.template().language(), message.template().params());
            } else if (message.type() == MessageType.INTERACTIVE && message.options() != null) {
                waMessageId = api.sendInteractive(credentials, message.toPhone(), message.body(), message.options());
            } else {
                waMessageId = api.sendText(credentials, message.toPhone(), message.body());
            }
            conversations.markSent(tenantId, messageId, waMessageId);
        } catch (WhatsAppApiException e) {
            if (PERMANENT.contains(e.code()) || job.isLastAttempt()) {
                conversations.markFailed(tenantId, messageId, e.code());
                return;
            }
            log.info("Send of message {} failed ({}); will retry", messageId, e.code());
            throw e;
        }
    }
}
