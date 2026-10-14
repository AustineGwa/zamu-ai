package ai.zamu.leads;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.jobs.JobQueue;
import ai.zamu.shared.jobs.NewJob;
import ai.zamu.shared.phone.KenyanPhone;

/** Website sign-ups: store them, tell the Zamu team, and track them in the admin console. */
@Service
public class LeadService {

    static final String NOTIFY_JOB = "notify_new_lead";

    private final LeadRepository leads;
    private final JobQueue jobs;
    private final AuditService audit;
    private final Clock clock;

    LeadService(LeadRepository leads, JobQueue jobs, AuditService audit, Clock clock) {
        this.leads = leads;
        this.jobs = jobs;
        this.audit = audit;
        this.clock = clock;
    }

    /** Stores a sign-up and queues the email to the team, in one transaction. */
    @Transactional
    public LeadView signUp(SignUpForm form) {
        String phone = KenyanPhone.normalise(form.phone()).orElseThrow(InvalidPhoneException::new);
        Instant now = clock.instant();
        Lead lead = new Lead(
                UuidV7.next(clock),
                form.businessName().trim(),
                form.contactName().trim(),
                phone,
                blankToNull(form.email()),
                form.businessType(),
                blankToNull(form.message()),
                LeadStatus.NEW,
                now,
                now,
                now);
        leads.insert(lead);
        // Only the ID goes in the job payload; the handler reads the rest from the table (NFR-13).
        jobs.enqueue(NewJob.of(NOTIFY_JOB, null, Map.of("leadId", lead.id().toString())));
        return lead.toView();
    }

    @Transactional(readOnly = true)
    public List<LeadView> recent(LeadStatus status) {
        return leads.findRecent(status, 200).stream().map(Lead::toView).toList();
    }

    @Transactional
    public void changeStatus(UUID leadId, LeadStatus status) {
        Lead before = leads.findById(leadId).orElseThrow(LeadNotFoundException::new);
        if (before.status() == status) {
            return;
        }
        leads.updateStatus(leadId, status, clock.instant());
        audit.record(AuditEvent.of(null, "lead.status_changed", "lead", leadId)
                .withBefore(Map.of("status", before.status().name()))
                .withAfter(Map.of("status", status.name())));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
