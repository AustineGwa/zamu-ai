package ai.zamu.pricing;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.web.NotFoundException;

/**
 * The public price list (pricing page, home page, sign-up form). Read from the database and cached; an admin edit
 * clears the cache so the website shows it straight away.
 */
@Service
public class PricingService {

    static final String CACHE = "pricing";

    private final PricingRepository repository;
    private final AuditService audit;
    private final Clock clock;

    PricingService(PricingRepository repository, AuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    /** Active plans in display order, the comparison table and the questions. */
    @Cacheable(CACHE)
    @Transactional(readOnly = true)
    public PricingCatalog catalog() {
        List<PricingPlan> plans = repository.findPlans(true);
        Map<String, Map<String, Map<String, String>>> grouped = new LinkedHashMap<>();
        for (PricingRepository.FeatureValue fv : repository.findFeatureValues()) {
            grouped.computeIfAbsent(fv.groupName(), g -> new LinkedHashMap<>())
                    .computeIfAbsent(fv.label(), l -> new LinkedHashMap<>())
                    .put(fv.planCode(), fv.value());
        }
        List<PricingCatalog.ComparisonGroup> groups = new ArrayList<>();
        grouped.forEach((group, rows) -> groups.add(new PricingCatalog.ComparisonGroup(group,
                rows.entrySet().stream()
                        .map(e -> new PricingCatalog.ComparisonRow(e.getKey(), Map.copyOf(e.getValue())))
                        .toList())));
        return new PricingCatalog(plans, groups, repository.findFaqs());
    }

    /** An active plan by its code, as used in /signup?plan=growth. */
    public Optional<PricingPlan> findActive(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return catalog().plans().stream().filter(p -> p.code().equalsIgnoreCase(code.trim())).findFirst();
    }

    /** Plan names by code, including inactive plans (old sign-ups keep their label). */
    @Transactional(readOnly = true)
    public Map<String, String> namesByCode() {
        Map<String, String> names = new LinkedHashMap<>();
        repository.findPlans(false).forEach(p -> names.put(p.code(), p.name()));
        return names;
    }

    // ---- Admin console ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PricingPlan> allPlans() {
        return repository.findPlans(false);
    }

    @CacheEvict(cacheNames = CACHE, allEntries = true)
    @Transactional
    public void updatePlan(UUID planId, PricingPlanForm form) {
        PricingPlan before = repository.findPlan(planId).orElseThrow(() -> new NotFoundException("Plan not found"));
        String highlights = form.highlights().lines().map(String::strip).filter(s -> !s.isEmpty())
                .reduce((a, b) -> a + "\n" + b).orElse("");
        repository.updatePlan(planId, form.name().trim(), form.tagline().trim(), form.audience().trim(),
                form.monthlyPrice(), form.priceNote().trim(), form.trialDays(), highlights, form.ctaLabel().trim(),
                blankToNull(form.badge()), form.featured(), form.sortOrder(), form.active(), clock.instant());
        audit.record(AuditEvent.of(null, "pricing.plan_updated", "pricing_plan", planId)
                .withBefore(Map.of("price", before.monthlyPrice().toPlainString(), "trialDays", before.trialDays(),
                        "active", before.active()))
                .withAfter(Map.of("price", form.monthlyPrice().toPlainString(), "trialDays", form.trialDays(),
                        "active", form.active())));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
