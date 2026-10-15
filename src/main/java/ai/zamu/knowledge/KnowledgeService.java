package ai.zamu.knowledge;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.jobs.JobQueue;
import ai.zamu.shared.jobs.NewJob;
import ai.zamu.shared.web.NotFoundException;

/**
 * The knowledge base editor (spec: Admin console, Knowledge base; routes /admin/tenants/{id}/kb). Entries start as
 * drafts; approving one queues its re-embedding (AI-12, within a minute); archiving removes it from search at once.
 */
@Service
public class KnowledgeService {

    static final String REEMBED_JOB = "reembed_kb_entry";

    private final KbEntryRepository entries;
    private final KbChunkRepository chunks;
    private final InMemoryRetriever retriever;
    private final JobQueue jobs;
    private final AuditService audit;
    private final Clock clock;

    KnowledgeService(KbEntryRepository entries, KbChunkRepository chunks, InMemoryRetriever retriever, JobQueue jobs,
            AuditService audit, Clock clock) {
        this.entries = entries;
        this.chunks = chunks;
        this.retriever = retriever;
        this.jobs = jobs;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<KbEntryView> list(TenantId tenantId) {
        return entries.findAll(tenantId).stream().map(KbEntry::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<KbEntryView> approved(TenantId tenantId) {
        return entries.findByStatus(tenantId, KbEntryStatus.APPROVED).stream().map(KbEntry::toView).toList();
    }

    @Transactional(readOnly = true)
    public int approvedCount(TenantId tenantId) {
        return entries.countByStatus(tenantId, KbEntryStatus.APPROVED);
    }

    @Transactional(readOnly = true)
    public KbEntryView get(TenantId tenantId, UUID entryId) {
        return find(tenantId, entryId).toView();
    }

    @Transactional
    public UUID create(TenantId tenantId, KbEntryForm form, UUID userId) {
        UUID id = UuidV7.next(clock);
        entries.insert(tenantId, id, form, clock.instant());
        audit.record(AuditEvent.of(tenantId, "kb.created", "kb_entry", id).withActor(userId)
                .withAfter(Map.of("title", form.title())));
        return id;
    }

    /** An approved entry stays approved and is re-embedded (AI-12: "approved or changed"). */
    @Transactional
    public void update(TenantId tenantId, UUID entryId, KbEntryForm form, UUID userId) {
        KbEntry before = find(tenantId, entryId);
        entries.update(tenantId, entryId, form, clock.instant());
        audit.record(AuditEvent.of(tenantId, "kb.updated", "kb_entry", entryId).withActor(userId)
                .withBefore(Map.of("answerEn", before.answerEn())).withAfter(Map.of("answerEn", form.answerEn())));
        if (before.status() == KbEntryStatus.APPROVED) {
            queueReembed(tenantId, entryId);
        }
    }

    @Transactional
    public void approve(TenantId tenantId, UUID entryId, UUID userId) {
        KbEntry entry = find(tenantId, entryId);
        entries.approve(tenantId, entryId, userId, clock.instant());
        audit.record(AuditEvent.of(tenantId, "kb.approved", "kb_entry", entryId).withActor(userId)
                .withBefore(entry.status().name()).withAfter(KbEntryStatus.APPROVED.name()));
        queueReembed(tenantId, entryId);
    }

    /** AI-12: archived entries are removed from search immediately, not by a job. */
    @Transactional
    public void archive(TenantId tenantId, UUID entryId, UUID userId) {
        KbEntry entry = find(tenantId, entryId);
        entries.setStatus(tenantId, entryId, KbEntryStatus.ARCHIVED, clock.instant());
        chunks.deleteForEntry(tenantId, entryId);
        retriever.evict(tenantId);
        audit.record(AuditEvent.of(tenantId, "kb.archived", "kb_entry", entryId).withActor(userId)
                .withBefore(entry.status().name()).withAfter(KbEntryStatus.ARCHIVED.name()));
    }

    /** Back to draft, e.g. when the client asks for a change before re-approving. Removed from search at once. */
    @Transactional
    public void unapprove(TenantId tenantId, UUID entryId, UUID userId) {
        find(tenantId, entryId);
        entries.setStatus(tenantId, entryId, KbEntryStatus.DRAFT, clock.instant());
        chunks.deleteForEntry(tenantId, entryId);
        retriever.evict(tenantId);
        audit.record(AuditEvent.of(tenantId, "kb.unapproved", "kb_entry", entryId).withActor(userId));
    }

    /** Re-embeds every approved entry, e.g. after switching the embedding provider. Returns how many were queued. */
    @Transactional
    public int reembedAll(TenantId tenantId) {
        List<KbEntry> approved = entries.findByStatus(tenantId, KbEntryStatus.APPROVED);
        approved.forEach(e -> queueReembed(tenantId, e.id()));
        return approved.size();
    }

    private void queueReembed(TenantId tenantId, UUID entryId) {
        jobs.enqueue(NewJob.of(REEMBED_JOB, tenantId, Map.of("entryId", entryId.toString())).lockKey("kb:" + entryId));
    }

    private KbEntry find(TenantId tenantId, UUID entryId) {
        return entries.findById(tenantId, entryId).orElseThrow(() -> new NotFoundException("Entry not found"));
    }

    /** NFR-11: offboarding purge of the knowledge base. */
    @Transactional
    public void purgeTenant(TenantId tenantId) {
        chunks.deleteAllForTenant(tenantId);
        entries.deleteAllForTenant(tenantId);
        retriever.evict(tenantId);
    }
}
