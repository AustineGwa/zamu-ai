package ai.zamu.conversations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ai.zamu.handovers.HandoverOutcome;
import ai.zamu.handovers.HandoverReason;
import ai.zamu.handovers.HandoverService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.jobs.JobQueue;
import ai.zamu.shared.jobs.NewJob;
import ai.zamu.shared.web.LiveUpdates;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;

import tools.jackson.databind.ObjectMapper;

/**
 * The conversation engine (spec: Messaging and the conversation engine): a deterministic state machine that decides
 * what each inbound message needs. It is channel-neutral: WhatsApp calls it today, voice will later.
 *
 * <p>Order of rules for each message:
 * <ol>
 *   <li>MSG-02 duplicate delivery: stop.</li>
 *   <li>MSG-04 find or create the customer and conversation; MSG-17 count a billable conversation.</li>
 *   <li>Store the message, open the 24-hour window (MSG-14), notify the inbox.</li>
 *   <li>Blocked customer, or a chat already with staff (MSG-08): store only.</li>
 *   <li>Staff-only or paused business (MSG-09): holding reply once, chat to the inbox.</li>
 *   <li>MSG-10 greeting for a new customer's first conversation.</li>
 *   <li>MSG-13 non-text; MSG-19 "stop"; MSG-18 "person"; greeting only; otherwise the answer step.</li>
 * </ol>
 * Runs in one job per message, one at a time per customer (MSG-05, job lock key). The AI step runs outside any
 * database transaction (it can take seconds); its result is applied in a second, short transaction, and dropped if
 * staff took the chat over in the meantime.
 */
@Component
class ConversationEngine {

    private static final Logger log = LoggerFactory.getLogger(ConversationEngine.class);

    enum Result { PROCESSED, DUPLICATE }

    static final String EMERGENCY_ALERT_JOB = "emergency_alert";
    static final int HISTORY_MESSAGES = 12;

    /** What the first transaction decided: done, or ask the AI step this question. */
    private record Step(Result result, AnswerProvider.Question question, UUID conversationId, String language) {

        static Step done(Result result) {
            return new Step(result, null, null, null);
        }
    }

    private final CustomerRepository customers;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final Outbox outbox;
    private final TenantService tenants;
    private final HandoverService handovers;
    private final AnswerProvider answers;
    private final LiveUpdates live;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final JobQueue jobs;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    ConversationEngine(CustomerRepository customers, ConversationRepository conversations, MessageRepository messages,
            Outbox outbox, TenantService tenants, HandoverService handovers, AnswerProvider answers, LiveUpdates live,
            ApplicationEventPublisher events, TransactionTemplate tx, JobQueue jobs, ObjectMapper objectMapper,
            Clock clock) {
        this.customers = customers;
        this.conversations = conversations;
        this.messages = messages;
        this.outbox = outbox;
        this.tenants = tenants;
        this.handovers = handovers;
        this.answers = answers;
        this.live = live;
        this.events = events;
        this.tx = tx;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Result handle(InboundMessage in) {
        Step step = tx.execute(s -> receive(in));
        if (step.question() == null) {
            return step.result();
        }
        AnswerProvider.Answer answer;
        try {
            answer = answers.answer(step.question());
        } catch (RuntimeException e) {
            // The message is already stored, so a retry would skip it as a duplicate: hand over instead (HO-01).
            log.error("AI step failed for conversation {} ({})", step.conversationId(), e.getClass().getSimpleName());
            answer = AnswerProvider.Answer.handover(HandoverReason.ERROR);
        }
        AnswerProvider.Answer decided = answer;
        tx.executeWithoutResult(s -> apply(step, decided));
        return Result.PROCESSED;
    }

    /** First transaction: store the message and handle everything that needs no AI. */
    private Step receive(InboundMessage in) {
        TenantId tenantId = in.tenantId();
        if (in.externalId() != null && messages.existsByWaMessageId(tenantId, in.externalId())) {
            return Step.done(Result.DUPLICATE); // MSG-02
        }
        Instant now = clock.instant();
        TenantView tenant = tenants.get(tenantId);

        // MSG-04: customer on first contact
        boolean newCustomer = false;
        Customer customer = customers.findByPhone(tenantId, in.phone()).orElse(null);
        if (customer == null) {
            UUID id = UuidV7.next(clock);
            customers.insert(tenantId, id, in.phone(), blankToNull(in.profileName()), now);
            customer = customers.findById(tenantId, id).orElseThrow();
            newCustomer = true;
        } else if (in.profileName() != null && customer.name() == null) {
            customers.updateNameIfMissing(tenantId, customer.id(), in.profileName().trim(), now);
        }

        // MSG-04: conversation, new when none is open or the last customer message is over 24 hours old
        Conversation previous = conversations.findLatest(tenantId, customer.id(), in.channel()).orElse(null);
        boolean newConversation = previous == null || !previous.continuesAt(in.receivedAt());
        Conversation conversation;
        if (newConversation) {
            if (previous != null && previous.status() != ConversationStatus.CLOSED) {
                conversations.updateStatus(tenantId, previous.id(), ConversationStatus.CLOSED, null, now);
                handovers.resolveSuperseded(tenantId, previous.id());
            }
            UUID id = UuidV7.next(clock);
            conversations.insert(tenantId, id, customer.id(), in.channel(), now);
            conversation = conversations.findById(tenantId, id).orElseThrow();
        } else {
            conversation = previous;
        }

        // MSG-17: billable once per customer per 24 hours from their first message
        if (conversation.billablePeriodExpired(now)) {
            conversations.startBillablePeriod(tenantId, conversation.id(), now);
            events.publishEvent(new ConversationEvents.BillableConversationStarted(tenantId, now));
        }

        // Store the message; MSG-14 window runs 24 hours from this message
        UUID inboundId = UuidV7.next(clock);
        messages.insert(tenantId, new Message(inboundId, tenantId.value(), conversation.id(),
                MessageDirection.IN, MessageSender.CUSTOMER, null, in.externalId(), in.type(), in.text(),
                in.mediaRef(), null, MessageStatus.RECEIVED, null, now, now));
        conversations.recordCustomerMessage(tenantId, conversation.id(), now);
        conversation = conversations.findById(tenantId, conversation.id()).orElseThrow();
        events.publishEvent(new ConversationEvents.MessageRecorded(tenantId, MessageDirection.IN, now));
        live.publish(tenantId, "conversation-" + conversation.id(), "");
        live.publish(tenantId, "inbox", conversation.id().toString());

        if (customer.blocked() || customer.deletedAt() != null) {
            return Step.done(Result.PROCESSED);
        }
        if (conversation.status() == ConversationStatus.HANDED_OVER) {
            return Step.done(Result.PROCESSED); // MSG-08: staff have it; never reply as the bot
        }

        String language = language(tenantId, customer, in.text(), tenant);

        // MSG-09: staff-only or paused
        if (!tenant.botActive()) {
            if (!conversation.holdingReplySent()) {
                String holding = tenant.holdingReply() != null ? tenant.holdingReply() : StandardMessages.holding(language);
                outbox.text(tenantId, conversation, MessageSender.BOT, null, holding);
                conversations.markHoldingReplySent(tenantId, conversation.id());
            }
            handOver(tenantId, conversation, HandoverReason.STAFF_ONLY, language, false);
            return Step.done(Result.PROCESSED);
        }

        // MSG-10: greeting at the start of a new customer's first conversation (Appendix B: "first message to a new customer")
        boolean greeted = false;
        if (newCustomer) {
            outbox.text(tenantId, conversation, MessageSender.BOT, null,
                    StandardMessages.greeting(language, tenant.displayName()));
            greeted = true;
        }

        if (in.type() == MessageType.MEDIA) {
            outbox.text(tenantId, conversation, MessageSender.BOT, null, StandardMessages.nonText(language)); // MSG-13
            return Step.done(Result.PROCESSED);
        }
        String text = in.text() == null ? "" : in.text();
        if (Keywords.isStop(text)) {
            customers.optOut(tenantId, customer.id(), now); // MSG-19
            outbox.text(tenantId, conversation, MessageSender.BOT, null, StandardMessages.optedOut(language));
            return Step.done(Result.PROCESSED);
        }
        if (Keywords.asksForPerson(text)) {
            handOver(tenantId, conversation, HandoverReason.REQUESTED, language, true); // MSG-18
            return Step.done(Result.PROCESSED);
        }
        if (greeted && Keywords.isGreetingOnly(text)) {
            return Step.done(Result.PROCESSED); // the greeting is the reply
        }

        return new Step(Result.PROCESSED, new AnswerProvider.Question(tenantId, conversation.id(), customer.id(), text,
                language, history(tenantId, conversation.id(), inboundId),
                in.replyId() != null ? in.replyId() : numberedChoice(tenantId, conversation.id(), text),
                customer.phone(), customer.name()), conversation.id(), language);
    }

    /**
     * A customer who types "2" instead of tapping the second option means that option: the latest message from
     * Zamu must be a list or buttons (BK-03). ASSUMPTION: also how typed replies to "Reply 1 to reschedule or 2 to
     * cancel" work, since the confirmation is sent with those two buttons.
     */
    private String numberedChoice(TenantId tenantId, UUID conversationId, String text) {
        String t = text.strip();
        if (!t.matches("\\d{1,2}")) {
            return null;
        }
        int n = Integer.parseInt(t);
        List<Message> recent = messages.findRecent(tenantId, conversationId, 4);
        for (int i = recent.size() - 1; i >= 0; i--) {
            Message m = recent.get(i);
            if (m.direction() != MessageDirection.OUT) {
                continue;
            }
            if (m.type() != MessageType.INTERACTIVE || m.templateJson() == null) {
                return null;
            }
            Interactive options = outbox.parseOptions(m.templateJson());
            return n >= 1 && n <= options.choices().size() ? options.choices().get(n - 1).id() : null;
        }
        return null;
    }

    /** Second transaction: send what the AI step decided, unless staff have taken the chat meanwhile (MSG-08). */
    private void apply(Step step, AnswerProvider.Answer answer) {
        TenantId tenantId = step.question().tenantId();
        Conversation conversation = conversations.findById(tenantId, step.conversationId()).orElse(null);
        if (conversation == null || conversation.status() != ConversationStatus.BOT) {
            log.info("Conversation {} changed while Zamu was answering; reply dropped", step.conversationId());
            return;
        }
        String language = step.language();
        switch (answer.kind()) {
            case REPLY -> {
                if (answer.options() != null) {
                    outbox.interactive(tenantId, conversation, answer.text(), answer.options()); // BK-03
                    return;
                }
                UUID messageId = outbox.text(tenantId, conversation, MessageSender.BOT, null, answer.text());
                messages.updateAiMeta(tenantId, messageId, answer.confidence(),
                        answer.entryIds().isEmpty() ? null : objectMapper.writeValueAsString(answer.entryIds()));
            }
            case NOT_SURE -> {
                UUID messageId = outbox.text(tenantId, conversation, MessageSender.BOT, null,
                        StandardMessages.notSure(language)); // AI-03
                messages.updateAiMeta(tenantId, messageId, answer.confidence(), null);
                handOver(tenantId, conversation, HandoverReason.LOW_CONFIDENCE, language, false);
            }
            case EMERGENCY -> {
                TenantView tenant = tenants.get(tenantId);
                outbox.text(tenantId, conversation, MessageSender.BOT, null,
                        StandardMessages.emergency(language, tenant.emergencyNumber())); // AI-04
                handovers.request(tenantId, conversation.id(), HandoverReason.SENSITIVE);
                conversations.updateStatus(tenantId, conversation.id(), ConversationStatus.HANDED_OVER, null,
                        clock.instant());
                // NOT-02: the owners hear at once, by email for now (no health details: NFR-11).
                jobs.enqueue(NewJob.of(EMERGENCY_ALERT_JOB, tenantId,
                        Map.of("conversationId", conversation.id().toString())));
            }
            case HANDOVER -> {
                if (answer.text() != null && !answer.text().isBlank()) {
                    outbox.text(tenantId, conversation, MessageSender.BOT, null, answer.text());
                }
                handOver(tenantId, conversation, answer.handover(), language, true);
            }
        }
    }

    /** The last messages before the question, for the AI step's context (spec: "the last 12 messages"). */
    private List<AnswerProvider.Turn> history(TenantId tenantId, UUID conversationId, UUID questionId) {
        List<Message> recent = messages.findRecent(tenantId, conversationId, HISTORY_MESSAGES + 3);
        List<AnswerProvider.Turn> turns = new ArrayList<>();
        for (Message m : recent) {
            if (m.id().equals(questionId) || m.body() == null || m.body().isBlank()) {
                continue;
            }
            String role = switch (m.sender()) {
                case CUSTOMER -> "customer";
                case STAFF -> "staff";
                case BOT, SYSTEM -> "zamu";
            };
            turns.add(new AnswerProvider.Turn(role, m.body()));
        }
        return turns.size() > HISTORY_MESSAGES ? turns.subList(turns.size() - HISTORY_MESSAGES, turns.size()) : turns;
    }

    /**
     * HO-01, HO-02: opens the handover, moves the conversation to staff and tells the customer: the handover message
     * (when {@code announce}) while staff are on duty, otherwise the after-hours message with their return time.
     */
    private void handOver(TenantId tenantId, Conversation conversation, HandoverReason reason, String language,
            boolean announce) {
        HandoverOutcome outcome = handovers.request(tenantId, conversation.id(), reason);
        conversations.updateStatus(tenantId, conversation.id(), ConversationStatus.HANDED_OVER, null, clock.instant());
        if (reason == HandoverReason.STAFF_ONLY) {
            return; // the holding reply already told the customer
        }
        if (outcome.staffOnDuty()) {
            if (announce) {
                outbox.text(tenantId, conversation, MessageSender.BOT, null, StandardMessages.handover(language));
            }
        } else {
            ZoneId zone = outcome.nextStaffTime() != null ? outcome.nextStaffTime().getZone() : ZoneId.of("Africa/Nairobi");
            LocalDate today = clock.instant().atZone(zone).toLocalDate();
            outbox.text(tenantId, conversation, MessageSender.BOT, null,
                    StandardMessages.afterHours(language, outcome.nextStaffTime(), today));
        }
        log.info("Conversation {} handed over ({})", conversation.id(), reason);
    }

    /** The customer's language: remembered, else guessed from this message, else the business default (AI-02). */
    private String language(TenantId tenantId, Customer customer, String text, TenantView tenant) {
        String guess = LanguageGuess.guess(text);
        if (guess != null && !guess.equals(customer.language())) {
            customers.updateLanguage(tenantId, customer.id(), guess, clock.instant());
            return guess;
        }
        if (customer.language() != null) {
            return customer.language();
        }
        return tenant.defaultLanguage().name().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
