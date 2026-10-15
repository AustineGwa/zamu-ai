package ai.zamu.ai;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import ai.zamu.conversations.AnswerProvider.Kind;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.jobs.Job;
import ai.zamu.shared.jobs.JobHandler;
import ai.zamu.shared.jobs.JobQueue;
import ai.zamu.shared.jobs.NewJob;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Job {@code run_eval}: asks one evaluation question through the real pipeline, records the verdict, and queues
 * the next question; the last one finishes the run. Idempotent: a retried question isn't recorded twice.
 */
@Component
class EvalRunHandler implements JobHandler {

    private final EvalRepository repository;
    private final AnswerService answers;
    private final AiProperties properties;
    private final JobQueue jobs;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    EvalRunHandler(EvalRepository repository, AnswerService answers, AiProperties properties, JobQueue jobs,
            ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.answers = answers;
        this.properties = properties;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String type() {
        return EvaluationService.RUN_JOB;
    }

    @Override
    public void handle(Job job) {
        TenantId tenantId = job.tenantId();
        JsonNode payload = objectMapper.readTree(job.payloadJson());
        UUID runId = UUID.fromString(payload.path("runId").asString(""));
        int index = payload.path("index").asInt(0);
        if (repository.markRunning(tenantId, runId) == 0) {
            return; // finished or gone
        }
        List<EvalCase> cases = repository.findCases(tenantId);
        if (index < cases.size()) {
            EvalCase c = cases.get(index);
            if (!repository.resultExists(tenantId, runId, c.id())) {
                AnswerService.Preview preview = answers.preview(tenantId, c.question(), c.language());
                EvalJudge.Verdict verdict = EvalJudge.judge(c, preview);
                Kind kind = preview.answer().kind();
                String reply = kind == Kind.REPLY ? preview.answer().text() : "[" + EvalJudge.describe(kind) + "]";
                repository.insertResult(tenantId, UuidV7.next(clock), runId, c.id(), reply, kind != Kind.REPLY,
                        preview.answer().confidence(), verdict.passed(), verdict.reason(), clock.instant());
            }
        }
        if (index + 1 < cases.size()) {
            jobs.enqueue(NewJob.of(EvaluationService.RUN_JOB, tenantId,
                    Map.of("runId", runId.toString(), "index", index + 1)).lockKey("eval:" + tenantId.value()));
        } else {
            int[] tally = repository.tally(tenantId, runId);
            repository.finishRun(tenantId, runId, "done", answers.liveModel() ? properties.modelSmall() : OfflineAiClient.MODEL,
                    tally[0], tally[1], tally[2], clock.instant());
        }
    }
}
