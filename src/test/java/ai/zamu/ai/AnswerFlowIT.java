package ai.zamu.ai;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ai.zamu.conversations.Channel;
import ai.zamu.conversations.ConversationService;
import ai.zamu.conversations.InboundMessage;
import ai.zamu.conversations.MessageType;
import ai.zamu.golive.GoLiveCheck;
import ai.zamu.golive.GoLiveService;
import ai.zamu.knowledge.KbEntryForm;
import ai.zamu.knowledge.KnowledgeService;
import ai.zamu.knowledge.Retriever;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.jobs.JobDrain;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.TenantFixture;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantStatus;
import ai.zamu.users.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Knowledge base, AI answers, evaluation and go-live on MySQL, with the offline AI stand-in (no API key in tests).
 * Spec: Knowledge base KB-01 to KB-05, AI answering AI-01 to AI-04, Evaluation set, Go-live checklist.
 */
class AnswerFlowIT extends AbstractMySqlIT {

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    @Autowired
    KnowledgeService knowledge;

    @Autowired
    Retriever retriever;

    @Autowired
    ConversationService conversations;

    @Autowired
    EvaluationService evaluation;

    @Autowired
    GoLiveService goLive;

    @Autowired
    JobDrain jobs;

    TenantFixture fx;
    int sequence;

    @BeforeEach
    void setUp() {
        fx = TenantFixture.create(tenants, users);
    }

    @Test
    void KB_02_onlyApprovedEntriesAreUsed() {
        UUID id = knowledge.create(fx.idA(), fillings(), fx.ownerA().id());
        jobs.drain();
        assertThat(retriever.retrieve(fx.idA(), "How much is a filling?", 6)).as("draft").isEmpty();

        knowledge.approve(fx.idA(), id, fx.ownerA().id());
        jobs.drain();

        assertThat(retriever.retrieve(fx.idA(), "How much is a filling?", 6))
                .extracting(Retriever.RetrievedChunk::entryId).contains(id);
    }

    @Test
    void KB_04_archivingRemovesAnEntryImmediately() {
        UUID id = approvedFillings(fx.idA());
        knowledge.archive(fx.idA(), id, fx.ownerA().id());

        assertThat(retriever.retrieve(fx.idA(), "How much is a filling?", 6)).isEmpty();
    }

    @Test
    void KB_03_editsToAnApprovedEntryAreReembedded() {
        UUID id = approvedFillings(fx.idA());
        knowledge.update(fx.idA(), id, new KbEntryForm("Fillings", "How much is a filling?",
                "Fillings cost KES 5,000 to KES 9,000 depending on size.", null, null), fx.ownerA().id());
        jobs.drain();

        assertThat(retriever.retrieve(fx.idA(), "How much is a filling?", 6)).singleElement()
                .satisfies(c -> assertThat(c.content()).contains("KES 5,000"));
    }

    @Test
    void NFR_09_entriesOfAnotherTenantAreNeverRetrieved() {
        approvedFillings(fx.idA());

        assertThat(retriever.retrieve(fx.idB(), "How much is a filling?", 6)).isEmpty();
    }

    @Test
    void AI_01_aQuestionTheKnowledgeBaseCoversIsAnsweredWithItsSource() {
        UUID id = approvedFillings(fx.idA());

        receive(fx.idA(), "How much is a filling?");

        assertThat(outbound(fx.idA())).last().asString().contains("KES 4,500");
        String sources = jdbc.sql("""
                SELECT kb_entry_ids FROM messages WHERE tenant_id = ? AND direction = 'out' AND ai_confidence IS NOT NULL
                """).param(fx.idA().bytes()).query(String.class).single();
        assertThat(sources).contains(id.toString());
        assertThat(conversationStatus(fx.idA())).isEqualTo("bot");
    }

    @Test
    void AI_04_NOT_02_anEmergencyGetsTheInstructionAndAlertsTheOwners() {
        receive(fx.idA(), "My child is not breathing, please help");

        assertThat(outbound(fx.idA())).last().asString().contains("999 or 112");
        assertThat(conversationStatus(fx.idA())).isEqualTo("handed_over");
        assertThat(jdbc.sql("SELECT reason FROM handovers WHERE tenant_id = ?").param(fx.idA().bytes())
                .query(String.class).single()).isEqualTo("sensitive");

        jobs.drain();
        assertThat(emails.sentTo("owner@afya.test")).anySatisfy(m ->
                assertThat(m.subject()).isEqualTo("Urgent: a customer may have an emergency"));
        assertThat(emails.sentTo("owner@baraka.test")).isEmpty();
    }

    @Test
    void EVAL_aRunMarksEveryCaseAndCountsTrapFailures() {
        approvedFillings(fx.idA());
        int traps = evaluation.addStarterTraps(fx.idA());
        UUID runId = evaluation.startRun(fx.idA(), fx.ownerA().id());

        jobs.drain();

        EvalRunView run = evaluation.run(fx.idA(), runId);
        assertThat(run.status()).isEqualTo("done");
        assertThat(run.total()).isEqualTo(traps);
        assertThat(run.trapFailures()).isZero();
        assertThat(evaluation.results(fx.idA(), runId)).hasSize(traps);
    }

    @Test
    void GOLIVE_requiredChecksBlockGoingLiveAndPausingStopsZamu() {
        List<GoLiveCheck> checks = goLive.checks(fx.idA());
        assertThat(goLive.ready(checks)).as("no WhatsApp, offline model, no knowledge").isFalse();
        assertThatThrownBy(() -> goLive.goLive(fx.idA())).isInstanceOf(IllegalStateException.class);

        assertThat(goLive.pause(fx.idA()).status()).isEqualTo(TenantStatus.PAUSED);
    }

    private UUID approvedFillings(TenantId tenantId) {
        UUID id = knowledge.create(tenantId, fillings(), fx.ownerA().id());
        knowledge.approve(tenantId, id, fx.ownerA().id());
        jobs.drain();
        return id;
    }

    private static KbEntryForm fillings() {
        return new KbEntryForm("Fillings", "How much is a filling?",
                "Fillings cost KES 4,500 to KES 8,000 depending on size.", null, null);
    }

    private void receive(TenantId tenantId, String text) {
        sequence++;
        conversations.receive(new InboundMessage(Channel.WHATSAPP, tenantId, "254712345678", "Wanjiku",
                "wamid.ai." + sequence, MessageType.TEXT, text, null, clock.instant()));
    }

    private List<String> outbound(TenantId tenantId) {
        return jdbc.sql("SELECT body FROM messages WHERE tenant_id = ? AND direction = 'out' ORDER BY id")
                .param(tenantId.bytes()).query(String.class).list();
    }

    private String conversationStatus(TenantId tenantId) {
        return jdbc.sql("SELECT status FROM conversations WHERE tenant_id = ? ORDER BY id DESC LIMIT 1")
                .param(tenantId.bytes()).query(String.class).single();
    }
}
