package ai.zamu.handovers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.zamu.bookings.BusinessHoursService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.mail.EmailMessage;
import ai.zamu.shared.mail.EmailSender;
import ai.zamu.users.UserService;

/**
 * HO-06: a handed-over chat with no staff reply for 30 minutes during handover hours escalates to the owner.
 * ASSUMPTION: the owner is alerted by email for now; WhatsApp owner alerts wait for the spec's open question
 * ("Owner alerts and daily summaries: separate Zamu WhatsApp number, or email and SMS only?").
 * Safe with several instances: each escalation is claimed once with a conditional update.
 */
@Component
class HandoverEscalator {

    private static final Logger log = LoggerFactory.getLogger(HandoverEscalator.class);
    static final Duration WAIT = Duration.ofMinutes(30);

    private final HandoverRepository repository;
    private final BusinessHoursService hours;
    private final UserService users;
    private final EmailSender email;
    private final Clock clock;
    private final String baseUrl;
    private final boolean enabled;

    HandoverEscalator(HandoverRepository repository, BusinessHoursService hours, UserService users, EmailSender email,
            Clock clock, @Value("${zamu.base-url}") String baseUrl, @Value("${zamu.jobs.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.hours = hours;
        this.users = users;
        this.email = email;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void scheduled() {
        if (!enabled) {
            return; // background work is off on this instance (zamu.jobs.enabled); tests call escalateDue()
        }
        try {
            escalateDue();
        } catch (RuntimeException e) {
            log.error("Handover escalation sweep failed", e);
        }
    }

    /** Returns how many handovers were escalated. Each claim is its own atomic update, so no transaction is needed. */
    public int escalateDue() {
        Instant now = clock.instant();
        List<Handover> candidates = repository.findEscalationCandidates(now.minus(WAIT), 100);
        int escalated = 0;
        for (Handover h : candidates) {
            TenantId tenantId = TenantId.of(h.tenantId());
            if (!hours.handoverOpen(tenantId, now) || !repository.claimEscalation(tenantId, h.id(), now)) {
                continue;
            }
            for (String owner : users.activeOwnerEmails(tenantId)) {
                email.send(new EmailMessage(owner, "A customer is waiting for a reply on Zamu", """
                        A customer has been waiting more than 30 minutes for someone from your team to reply.

                        Open the chat: %s/app/conversations/%s

                        Zamu""".formatted(baseUrl, h.conversationId())));
            }
            escalated++;
        }
        return escalated;
    }
}
