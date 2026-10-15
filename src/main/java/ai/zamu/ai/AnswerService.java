package ai.zamu.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import ai.zamu.bookings.BusinessHoursService;
import ai.zamu.conversations.AnswerProvider;
import ai.zamu.handovers.HandoverReason;
import ai.zamu.knowledge.Retriever;
import ai.zamu.knowledge.Retriever.RetrievedChunk;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.time.ZamuTime;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;

/**
 * The AI step (spec: AI answering, "How an answer is produced"):
 * <ol>
 *   <li>AI-04 emergency words and HO-01 "always hand over" topics go straight to staff, without a model call;</li>
 *   <li>retrieve the top 6 approved chunks (AI-01);</li>
 *   <li>build the prompt and call the small model for structured output; on invalid output or a failed guardrail,
 *       try the large model once (AI-09); on errors and timeouts the backup provider, then a handover (AI-10);</li>
 *   <li>guardrails: numbers only from the context (AI-05), no leaked instructions (AI-08), length (AI-07), and only
 *       entry IDs that were retrieved;</li>
 *   <li>below the tenant's confidence threshold, or with nothing relevant retrieved, "not sure" and a handover
 *       (AI-03).</li>
 * </ol>
 * Tokens and cost of each call are recorded (AI-11).
 */
@Service
public class AnswerService implements AnswerProvider {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);
    static final int TOP_K = 6;

    /** What the pipeline decided, with details for the admin console's preview and the evaluation runner. */
    public record Preview(Answer answer, String model, String failure, List<RetrievedChunk> chunks) {
    }

    private final TenantService tenants;
    private final BusinessHoursService hours;
    private final Retriever retriever;
    private final AiClient ai;
    private final PromptBuilder prompts;
    private final AiReplyParser parser;
    private final AiProperties properties;
    private final ApplicationEventPublisher events;
    private final BookingDialog bookingDialog;
    private final Clock clock;
    private final Cache<String, AtomicInteger> repliesPerCustomer = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(100_000)
            .build();

    AnswerService(TenantService tenants, BusinessHoursService hours, Retriever retriever, AiClient ai,
            PromptBuilder prompts, AiReplyParser parser, AiProperties properties, ApplicationEventPublisher events,
            BookingDialog bookingDialog, Clock clock) {
        this.tenants = tenants;
        this.hours = hours;
        this.retriever = retriever;
        this.ai = ai;
        this.prompts = prompts;
        this.parser = parser;
        this.properties = properties;
        this.events = events;
        this.bookingDialog = bookingDialog;
        this.clock = clock;
    }

    @Override
    public Answer answer(Question question) {
        Preview preview = decide(question, true);
        if (preview.failure() != null) {
            log.info("AI step for conversation {}: {} ({})", question.conversationId(), preview.answer().kind(),
                    preview.failure());
        }
        return preview.answer();
    }

    /** The same pipeline for a typed question, without a conversation (admin console and evaluation set). */
    public Preview preview(TenantId tenantId, String text, String language) {
        return decide(new Question(tenantId, null, null, text, language, List.of()), false);
    }

    /** Whether a real model is configured; false means the offline stand-in (local and tests only). */
    public boolean liveModel() {
        return ai.live();
    }

    private Preview decide(Question q, boolean countTowardsLimit) {
        TenantView tenant = tenants.get(q.tenantId());
        String language = "sw".equals(q.language()) ? "sw" : "en";
        if (SafetyKeywords.emergency(q.text())) {
            return new Preview(Answer.emergency(), null, "emergency words", List.of());
        }
        if (countTowardsLimit) {
            // BK-03, BK-08: a tapped booking option, or a numbered reply to a reminder, skips the model.
            Optional<Answer> booking = bookingDialog.reply(q);
            if (booking.isPresent()) {
                return new Preview(booking.get(), null, null, List.of());
            }
        }
        if (SafetyKeywords.mentionsAny(q.text(), tenant.handoverTopicList())) {
            return new Preview(Answer.handover(HandoverReason.TOPIC), null, "handover topic", List.of());
        }
        if (countTowardsLimit && overLimit(q)) {
            return new Preview(Answer.handover(HandoverReason.LIMIT), null, "reply limit reached (MSG-16)", List.of());
        }

        List<RetrievedChunk> chunks = retriever.retrieve(q.tenantId(), q.text(), TOP_K);
        PromptBuilder.Prompt prompt = prompts.build(tenant, hours.week(q.tenantId()), chunks, q.history(), q.text(),
                language, clock.instant().atZone(ZamuTime.NAIROBI));
        Set<UUID> retrieved = new HashSet<>();
        chunks.forEach(c -> retrieved.add(c.entryId()));

        AiReply reply = null;
        String model = null;
        String failure = null;
        for (ModelTier tier : ModelTier.values()) {
            AiClient.AiCompletion completion;
            try {
                completion = ai.complete(new AiClient.AiRequest(prompt.system(), prompt.user(), tier, chunks, language,
                        q.text()));
            } catch (AiUnavailableException e) {
                return new Preview(Answer.handover(HandoverReason.ERROR), model, "AI unavailable: " + e.getMessage(), chunks);
            }
            model = completion.model();
            recordUsage(q.tenantId(), completion);
            Optional<AiReply> parsed = parser.parse(completion.text());
            if (parsed.isEmpty()) {
                failure = "output was not valid JSON of the agreed shape";
                continue;
            }
            AiReply candidate = parsed.get();
            Optional<String> problem = validate(candidate, prompt.context(), retrieved);
            if (problem.isPresent()) {
                failure = problem.get();
                continue;
            }
            reply = candidate;
            failure = null;
            break;
        }
        if (reply == null) {
            return new Preview(Answer.notSure(null), model, failure, chunks);
        }
        return new Preview(route(q, reply, tenant, chunks), model, failure, chunks);
    }

    /** Checks the reply text, when there is one, and that it only cites retrieved entries. */
    private static Optional<String> validate(AiReply reply, String context, Set<UUID> retrieved) {
        if (!retrieved.containsAll(reply.usedEntryIds())) {
            return Optional.of("cited an entry that was not retrieved");
        }
        if (reply.reply().isBlank()) {
            return Optional.empty(); // fine: routed to not sure or a handover
        }
        return Guardrails.check(reply.reply(), context);
    }

    private Answer route(Question q, AiReply reply, TenantView tenant, List<RetrievedChunk> chunks) {
        BigDecimal confidence = BigDecimal.valueOf(reply.confidence()).setScale(2, RoundingMode.HALF_UP);
        switch (reply.intent()) {
            case "emergency":
                return Answer.emergency();
            case "talk_to_person":
                return Answer.handover(HandoverReason.REQUESTED);
            case "book", "reschedule", "cancel":
                return bookingDialog.start(q, reply.intent()); // Milestone 5
            case "pay":
                return Answer.handover(HandoverReason.BOOKING); // until Milestone 6
            case "greeting":
                if (!reply.reply().isBlank() && Guardrails.numbers(reply.reply()).isEmpty()) {
                    return Answer.reply(reply.reply(), confidence, List.of());
                }
                return Answer.notSure(confidence);
            default: // question and other: must be grounded (AI-01, AI-03)
                boolean grounded = !chunks.isEmpty() && !reply.usedEntryIds().isEmpty() && !reply.reply().isBlank();
                if (!grounded || confidence.compareTo(tenant.confidenceThreshold()) < 0) {
                    return Answer.notSure(confidence);
                }
                return Answer.reply(reply.reply(), confidence, reply.usedEntryIds());
        }
    }

    /** MSG-16 (ASSUMPTION): a volume cap per customer per hour stands in for abuse detection in version 1. */
    private boolean overLimit(Question q) {
        if (q.customerId() == null) {
            return false;
        }
        AtomicInteger count = repliesPerCustomer.get(q.tenantId().value() + ":" + q.customerId(), k -> new AtomicInteger());
        return count.incrementAndGet() > properties.maxRepliesPerCustomerHour();
    }

    private void recordUsage(TenantId tenantId, AiClient.AiCompletion c) {
        if (c.tokensIn() == 0 && c.tokensOut() == 0) {
            return;
        }
        events.publishEvent(new AiUsageRecorded(tenantId, c.model(), c.tokensIn(), c.tokensOut(),
                properties.cost(c.model(), c.tokensIn(), c.tokensOut()), clock.instant()));
    }
}
