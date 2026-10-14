package ai.zamu.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

/** pricing_plans, pricing_features, pricing_plan_features and pricing_faqs. A public catalogue, not tenant data. */
@Component
class PricingRepository {

    private static final String WHY = "The public price list belongs to Zamu, not to any tenant";

    private static final String PLAN_COLUMNS = """
            id, code, name, tagline, audience, monthly_price, currency, price_note, trial_days, highlights, cta_label,
            badge, featured, sort_order, active""";

    private static final RowMapper<PricingPlan> PLAN = (rs, n) -> new PricingPlan(
            Uuids.read(rs, "id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("tagline"),
            rs.getString("audience"),
            rs.getBigDecimal("monthly_price"),
            rs.getString("currency"),
            rs.getString("price_note"),
            rs.getInt("trial_days"),
            lines(rs.getString("highlights")),
            rs.getString("cta_label"),
            rs.getString("badge"),
            rs.getBoolean("featured"),
            rs.getInt("sort_order"),
            rs.getBoolean("active"));

    record FeatureValue(String groupName, String label, int sortOrder, String planCode, String value) {
    }

    private final JdbcClient jdbc;

    PricingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @CrossTenant(WHY)
    List<PricingPlan> findPlans(boolean activeOnly) {
        return jdbc.sql("SELECT " + PLAN_COLUMNS + " FROM pricing_plans"
                        + (activeOnly ? " WHERE active = TRUE" : "") + " ORDER BY sort_order, name")
                .query(PLAN)
                .list();
    }

    @CrossTenant(WHY)
    Optional<PricingPlan> findPlan(UUID id) {
        return jdbc.sql("SELECT " + PLAN_COLUMNS + " FROM pricing_plans WHERE id = :id")
                .param("id", Uuids.toBytes(id))
                .query(PLAN)
                .optional();
    }

    /** Every feature with its value for each active plan, in table order. */
    @CrossTenant(WHY)
    List<FeatureValue> findFeatureValues() {
        return jdbc.sql("""
                SELECT f.group_name, f.label, f.sort_order, p.code, pf.feature_value
                  FROM pricing_features f
                  JOIN pricing_plan_features pf ON pf.feature_id = f.id
                  JOIN pricing_plans p ON p.id = pf.plan_id AND p.active = TRUE
                 ORDER BY f.sort_order, f.label
                """)
                .query((rs, n) -> new FeatureValue(rs.getString("group_name"), rs.getString("label"),
                        rs.getInt("sort_order"), rs.getString("code"), rs.getString("feature_value")))
                .list();
    }

    @CrossTenant(WHY)
    List<PricingCatalog.Faq> findFaqs() {
        return jdbc.sql("SELECT question, answer FROM pricing_faqs WHERE active = TRUE ORDER BY sort_order, question")
                .query((rs, n) -> new PricingCatalog.Faq(rs.getString("question"), rs.getString("answer")))
                .list();
    }

    @CrossTenant(WHY)
    int updatePlan(UUID id, String name, String tagline, String audience, BigDecimal monthlyPrice, String priceNote,
            int trialDays, String highlights, String ctaLabel, String badge, boolean featured, int sortOrder,
            boolean active, Instant now) {
        return jdbc.sql("""
                UPDATE pricing_plans
                   SET name = :name, tagline = :tagline, audience = :audience, monthly_price = :price,
                       price_note = :priceNote, trial_days = :trialDays, highlights = :highlights, cta_label = :cta,
                       badge = :badge, featured = :featured, sort_order = :sortOrder, active = :active,
                       updated_at = :now
                 WHERE id = :id
                """)
                .param("id", Uuids.toBytes(id))
                .param("name", name)
                .param("tagline", tagline)
                .param("audience", audience)
                .param("price", monthlyPrice)
                .param("priceNote", priceNote)
                .param("trialDays", trialDays)
                .param("highlights", highlights)
                .param("cta", ctaLabel)
                .param("badge", badge)
                .param("featured", featured)
                .param("sortOrder", sortOrder)
                .param("active", active)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    private static List<String> lines(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
