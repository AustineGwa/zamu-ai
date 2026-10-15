package ai.zamu.conversations;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.jobs.JobQueue;
import ai.zamu.shared.jobs.NewJob;
import ai.zamu.shared.web.LiveUpdates;

import tools.jackson.databind.ObjectMapper;

/**
 * Stores an outbound message as queued and enqueues its delivery in the same transaction (the job runs only after
 * commit). Jobs for one conversation share a lock key, so messages go out in the order they were written (MSG-05).
 */
@Component
class Outbox {

    static final String SEND_JOB = OutboundMessage.SEND_JOB;

    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final JobQueue jobs;
    private final LiveUpdates live;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    Outbox(MessageRepository messages, ConversationRepository conversations, JobQueue jobs, LiveUpdates live,
            ApplicationEventPublisher events, ObjectMapper objectMapper, Clock clock) {
        this.messages = messages;
        this.conversations = conversations;
        this.jobs = jobs;
        this.live = live;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    UUID text(TenantId tenantId, Conversation conversation, MessageSender sender, UUID userId, String text) {
        return queue(tenantId, conversation, sender, userId, MessageType.TEXT, text, null);
    }

    UUID template(TenantId tenantId, Conversation conversation, UUID userId, TemplateRef template, String renderedBody) {
        return queue(tenantId, conversation, MessageSender.STAFF, userId, MessageType.TEMPLATE, renderedBody,
                objectMapper.writeValueAsString(template));
    }

    /** A template Zamu sends itself, such as the booking reminder (BK-11). */
    UUID botTemplate(TenantId tenantId, Conversation conversation, TemplateRef template, String renderedBody) {
        return queue(tenantId, conversation, MessageSender.BOT, null, MessageType.TEMPLATE, renderedBody,
                objectMapper.writeValueAsString(template));
    }

    /**
     * Options to tap (BK-03). The options are stored with the message (in template_json, which holds the
     * channel payload for non-text messages) and the body lists them as text for the inbox and other channels.
     */
    UUID interactive(TenantId tenantId, Conversation conversation, String text, Interactive options) {
        return queue(tenantId, conversation, MessageSender.BOT, null, MessageType.INTERACTIVE, text,
                objectMapper.writeValueAsString(options));
    }

    private UUID queue(TenantId tenantId, Conversation conversation, MessageSender sender, UUID userId,
            MessageType type, String body, String templateJson) {
        Instant now = clock.instant();
        UUID id = UuidV7.next(clock);
        messages.insert(tenantId, new Message(id, tenantId.value(), conversation.id(), MessageDirection.OUT, sender,
                userId, null, type, body, null, templateJson, MessageStatus.QUEUED, null, now, now));
        conversations.touch(tenantId, conversation.id(), now);
        if (conversation.channel() == Channel.WHATSAPP) {
            jobs.enqueue(NewJob.of(SEND_JOB, tenantId, Map.of("messageId", id.toString()))
                    .lockKey("out:" + conversation.id()));
        }
        events.publishEvent(new ConversationEvents.MessageRecorded(tenantId, MessageDirection.OUT, now));
        if (type == MessageType.TEMPLATE && conversation.channel() == Channel.WHATSAPP) {
            events.publishEvent(new ConversationEvents.TemplateQueued(tenantId, now));
        }
        live.publish(tenantId, "conversation-" + conversation.id(), id.toString());
        live.publish(tenantId, "inbox", conversation.id().toString());
        return id;
    }

    Interactive parseOptions(String json) {
        return json == null ? null : objectMapper.readValue(json, Interactive.class);
    }

    TemplateRef parseTemplate(String json) {
        return json == null ? null : objectMapper.readValue(json, TemplateRef.class);
    }
}
