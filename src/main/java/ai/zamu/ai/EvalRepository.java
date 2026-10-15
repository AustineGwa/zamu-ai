package ai.zamu.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.time.ZamuTime;

/** eval_cases, eval_runs and eval_results. */
@Component
class EvalRepository {

    private static final RowMapper<EvalCase> CASE = (rs, n) -> new EvalCase(Uuids.read(rs, "id"),
            EvalCaseKind.fromDb(rs.getString("kind")), rs.getString("language"), rs.getString("question"),
            rs.getString("expected_answer"), rs.getString("must_include"), rs.getBoolean("active"));

    private static final RowMapper<EvalRunView> RUN = (rs, n) -> new EvalRunView(Uuids.read(rs, "id"),
            rs.getString("status"), rs.getString("model"), rs.getInt("total"), rs.getInt("passed"),
            rs.getInt("trap_failures"), ZamuTime.read(rs, "created_at"), ZamuTime.read(rs, "finished_at"));

    private final JdbcClient jdbc;

    EvalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<EvalCase> findCases(TenantId tenantId) {
        return jdbc.sql("""
                SELECT id, kind, language, question, expected_answer, must_include, active
                  FROM eval_cases WHERE tenant_id = :tenantId AND active = TRUE ORDER BY kind, created_at, id
                """)
                .param("tenantId", tenantId.bytes())
                .query(CASE)
                .list();
    }

    int countCases(TenantId tenantId) {
        return jdbc.sql("SELECT COUNT(*) FROM eval_cases WHERE tenant_id = :tenantId AND active = TRUE")
                .param("tenantId", tenantId.bytes())
                .query(Integer.class)
                .single();
    }

    void insertCase(TenantId tenantId, UUID id, EvalCaseForm form, Instant now) {
        jdbc.sql("""
                INSERT INTO eval_cases (id, tenant_id, kind, language, question, expected_answer, must_include, active,
                                        created_at, updated_at)
                VALUES (:id, :tenantId, :kind, :language, :question, :expected, :mustInclude, TRUE, :now, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("kind", form.kind().dbValue())
                .param("language", form.language())
                .param("question", form.question().strip())
                .param("expected", blankToNull(form.expectedAnswer()))
                .param("mustInclude", blankToNull(form.mustInclude()))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    /** Cases are kept for old results; removing one only hides it from future runs. */
    int deactivateCase(TenantId tenantId, UUID id, Instant now) {
        return jdbc.sql("UPDATE eval_cases SET active = FALSE, updated_at = :now WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void insertRun(TenantId tenantId, UUID id, UUID requestedBy, Instant now) {
        jdbc.sql("""
                INSERT INTO eval_runs (id, tenant_id, status, requested_by, created_at)
                VALUES (:id, :tenantId, 'queued', :requestedBy, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("requestedBy", requestedBy == null ? null : Uuids.toBytes(requestedBy))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int markRunning(TenantId tenantId, UUID id) {
        return jdbc.sql("UPDATE eval_runs SET status = 'running' WHERE tenant_id = :tenantId AND id = :id AND status IN ('queued', 'running')")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .update();
    }

    void finishRun(TenantId tenantId, UUID id, String status, String model, int total, int passed, int trapFailures,
            Instant now) {
        jdbc.sql("""
                UPDATE eval_runs SET status = :status, model = :model, total = :total, passed = :passed,
                                     trap_failures = :traps, finished_at = :now
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("status", status)
                .param("model", model)
                .param("total", total)
                .param("passed", passed)
                .param("traps", trapFailures)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void deleteResults(TenantId tenantId, UUID runId) {
        jdbc.sql("DELETE FROM eval_results WHERE tenant_id = :tenantId AND run_id = :runId")
                .param("tenantId", tenantId.bytes())
                .param("runId", Uuids.toBytes(runId))
                .update();
    }

    boolean resultExists(TenantId tenantId, UUID runId, UUID caseId) {
        return jdbc.sql("SELECT COUNT(*) FROM eval_results WHERE tenant_id = :tenantId AND run_id = :runId AND case_id = :caseId")
                .param("tenantId", tenantId.bytes())
                .param("runId", Uuids.toBytes(runId))
                .param("caseId", Uuids.toBytes(caseId))
                .query(Integer.class)
                .single() > 0;
    }

    /** Totals of a run so far: [total, passed, trap failures]. */
    int[] tally(TenantId tenantId, UUID runId) {
        return jdbc.sql("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(CASE WHEN r.passed THEN 1 ELSE 0 END), 0) AS passed,
                       COALESCE(SUM(CASE WHEN r.passed = FALSE AND c.kind <> 'answer' AND r.reason LIKE 'trap:%' THEN 1 ELSE 0 END), 0) AS traps
                  FROM eval_results r JOIN eval_cases c ON c.id = r.case_id
                 WHERE r.tenant_id = :tenantId AND r.run_id = :runId
                """)
                .param("tenantId", tenantId.bytes())
                .param("runId", Uuids.toBytes(runId))
                .query((rs, n) -> new int[] {rs.getInt("total"), rs.getInt("passed"), rs.getInt("traps")})
                .single();
    }

    void insertResult(TenantId tenantId, UUID id, UUID runId, UUID caseId, String reply, boolean handedOver,
            BigDecimal confidence, boolean passed, String reason, Instant now) {
        jdbc.sql("""
                INSERT INTO eval_results (id, tenant_id, run_id, case_id, reply, handed_over, confidence, passed, reason, created_at)
                VALUES (:id, :tenantId, :runId, :caseId, :reply, :handedOver, :confidence, :passed, :reason, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("runId", Uuids.toBytes(runId))
                .param("caseId", Uuids.toBytes(caseId))
                .param("reply", reply)
                .param("handedOver", handedOver)
                .param("confidence", confidence)
                .param("passed", passed)
                .param("reason", reason == null ? null : reason.length() > 300 ? reason.substring(0, 300) : reason)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    Optional<EvalRunView> findRun(TenantId tenantId, UUID id) {
        return jdbc.sql("""
                SELECT id, status, model, total, passed, trap_failures, created_at, finished_at
                  FROM eval_runs WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .query(RUN)
                .optional();
    }

    List<EvalRunView> recentRuns(TenantId tenantId, int limit) {
        return jdbc.sql("""
                SELECT id, status, model, total, passed, trap_failures, created_at, finished_at
                  FROM eval_runs WHERE tenant_id = :tenantId ORDER BY created_at DESC, id DESC LIMIT :limit
                """)
                .param("tenantId", tenantId.bytes())
                .param("limit", limit)
                .query(RUN)
                .list();
    }

    List<EvalResultView> results(TenantId tenantId, UUID runId) {
        return jdbc.sql("""
                SELECT r.case_id, c.kind, c.language, c.question, c.expected_answer, c.must_include, r.reply,
                       r.handed_over, r.confidence, r.passed, r.reason
                  FROM eval_results r JOIN eval_cases c ON c.id = r.case_id AND c.tenant_id = r.tenant_id
                 WHERE r.tenant_id = :tenantId AND r.run_id = :runId
                 ORDER BY r.passed, c.kind, r.created_at, r.id
                """)
                .param("tenantId", tenantId.bytes())
                .param("runId", Uuids.toBytes(runId))
                .query((rs, n) -> new EvalResultView(Uuids.read(rs, "case_id"), EvalCaseKind.fromDb(rs.getString("kind")),
                        rs.getString("language"), rs.getString("question"), rs.getString("expected_answer"),
                        rs.getString("must_include"), rs.getString("reply"), rs.getBoolean("handed_over"),
                        rs.getBigDecimal("confidence"), rs.getBoolean("passed"), rs.getString("reason")))
                .list();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    void deleteAllForTenant(TenantId tenantId) {
        jdbc.sql("DELETE FROM eval_results WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
        jdbc.sql("DELETE FROM eval_runs WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
        jdbc.sql("DELETE FROM eval_cases WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
    }
}
