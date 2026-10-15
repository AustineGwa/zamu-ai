package ai.zamu.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.pricing.PricingPlan;
import ai.zamu.pricing.PricingService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.mail.EmailMessage;
import ai.zamu.shared.mail.EmailSender;
import ai.zamu.shared.time.ZamuTime;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantStatus;
import ai.zamu.tenants.TenantView;
import ai.zamu.users.UserService;

/**
 * Usage and billing for the Zamu team (REP-03 monthly usage against the plan limit, REP-04 margin, REP-05 CSV) and
 * the running-cost alert (NFR-19). Running cost = AI cost plus WhatsApp template messages, in KES. Hosting and
 * staff time are not included (ASSUMPTION).
 */
@Service
public class UsageReportService {

    private static final Logger log = LoggerFactory.getLogger(UsageReportService.class);
    static final String COST_ALERT = "cost_alert";

    /** One tenant's month. {@code costSharePercent} is null when the plan fee is unknown or zero. */
    public record UsageRow(UUID tenantId, String tenant, String plan, TenantStatus status, int conversations,
            int conversationLimit, int overage, int messagesIn, int messagesOut, BigDecimal aiCostUsd,
            int waBillableMessages, BigDecimal runningCostKes, BigDecimal planFeeKes, Integer costSharePercent,
            boolean flagged) {
    }

    private final ReportsRepository repository;
    private final TenantService tenants;
    private final PricingService pricing;
    private final BillingProperties billing;
    private final UserService users;
    private final EmailSender email;
    private final Clock clock;
    private final String baseUrl;
    private final boolean enabled;

    UsageReportService(ReportsRepository repository, TenantService tenants, PricingService pricing,
            BillingProperties billing, UserService users, EmailSender email, Clock clock,
            @Value("${zamu.base-url}") String baseUrl, @Value("${zamu.jobs.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.tenants = tenants;
        this.pricing = pricing;
        this.billing = billing;
        this.users = users;
        this.email = email;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    public YearMonth currentMonth() {
        return YearMonth.now(clock.withZone(ZamuTime.NAIROBI));
    }

    /** Every tenant that isn't offboarded, plus offboarded ones with usage in the month. Highest cost share first. */
    @Transactional(readOnly = true)
    public List<UsageRow> month(YearMonth month) {
        Map<UUID, ReportsRepository.TenantMonthUsage> usage = new HashMap<>();
        repository.monthUsage(month).forEach(u -> usage.put(u.tenantId(), u));
        List<UsageRow> rows = new ArrayList<>();
        for (TenantView t : tenants.listAll()) {
            ReportsRepository.TenantMonthUsage u = usage.get(t.id());
            if (u == null && t.status() == TenantStatus.OFFBOARDED) {
                continue;
            }
            int conversations = u == null ? 0 : u.conversations();
            BigDecimal ai = u == null ? BigDecimal.ZERO : u.aiCostUsd();
            int wa = u == null ? 0 : u.waBillableMessages();
            BigDecimal cost = ai.add(billing.whatsappMessageUsd().multiply(BigDecimal.valueOf(wa)))
                    .multiply(billing.usdToKes()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = planFee(t);
            Integer share = fee == null || fee.signum() == 0 ? null
                    : cost.multiply(BigDecimal.valueOf(100)).divide(fee, 0, RoundingMode.HALF_UP).intValue();
            boolean flagged = fee != null && fee.signum() > 0 && cost.compareTo(fee.multiply(billing.alertShare())) > 0;
            rows.add(new UsageRow(t.id(), t.displayName(), t.plan().label(), t.status(), conversations,
                    t.conversationLimit(), Math.max(0, conversations - t.conversationLimit()),
                    u == null ? 0 : u.messagesIn(), u == null ? 0 : u.messagesOut(), ai.setScale(4, RoundingMode.HALF_UP),
                    wa, cost, fee, share, flagged));
        }
        rows.sort(Comparator.comparing((UsageRow r) -> r.costSharePercent() == null ? -1 : r.costSharePercent())
                .reversed().thenComparing(UsageRow::tenant));
        return rows;
    }

    /** The plan fee from the public price list (pricing_plans), falling back to the plan's built-in fee. */
    private BigDecimal planFee(TenantView t) {
        return pricing.findActive(t.plan().name().toLowerCase(Locale.ROOT)).map(PricingPlan::monthlyPrice)
                .orElse(BigDecimal.valueOf(t.plan().monthlyFeeKes()));
    }

    /** REP-05: for invoicing. */
    public String csv(YearMonth month, List<UsageRow> rows) {
        StringBuilder out = new StringBuilder("month,tenant,plan,status,billable_conversations,plan_limit,overage,"
                + "messages_in,messages_out,ai_cost_usd,whatsapp_billable_messages,running_cost_kes,plan_fee_kes,"
                + "cost_share_percent,flagged\n");
        for (UsageRow r : rows) {
            out.append(month).append(',').append(ReportService.csvCell(r.tenant())).append(',')
                    .append(ReportService.csvCell(r.plan())).append(',').append(r.status().name().toLowerCase(Locale.ROOT))
                    .append(',').append(r.conversations()).append(',').append(r.conversationLimit()).append(',')
                    .append(r.overage()).append(',').append(r.messagesIn()).append(',').append(r.messagesOut()).append(',')
                    .append(r.aiCostUsd().toPlainString()).append(',').append(r.waBillableMessages()).append(',')
                    .append(r.runningCostKes().toPlainString()).append(',')
                    .append(r.planFeeKes() == null ? "" : r.planFeeKes().toPlainString()).append(',')
                    .append(r.costSharePercent() == null ? "" : r.costSharePercent()).append(',')
                    .append(r.flagged() ? "yes" : "no").append('\n');
        }
        return out.toString();
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
    void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            sendCostAlerts();
        } catch (RuntimeException e) {
            log.error("Cost alert check failed", e);
        }
    }

    /** NFR-19: tells the Zamu team once a month when a tenant's running cost passes the alert share of their fee. */
    public int sendCostAlerts() {
        YearMonth month = currentMonth();
        int sent = 0;
        for (UsageRow r : month(month)) {
            if (!r.flagged() || !repository.claimDelivery(TenantId.of(r.tenantId()), COST_ALERT, month.atDay(1),
                    clock.instant())) {
                continue;
            }
            for (String operator : users.activePlatformEmails()) {
                email.send(new EmailMessage(operator, "Running cost above " + billing.alertShare()
                        .multiply(BigDecimal.valueOf(100)).intValue() + "% of plan fee: " + r.tenant(), """
                        %s has used KES %s of running cost so far this month (%s%% of their KES %s plan fee):
                        %d conversations (plan limit %d), %d WhatsApp template messages, AI cost USD %s.

                        Usage and billing: %s/admin/usage?month=%s

                        Zamu""".formatted(r.tenant(), r.runningCostKes().toPlainString(), r.costSharePercent(),
                        r.planFeeKes().toPlainString(), r.conversations(), r.conversationLimit(), r.waBillableMessages(),
                        r.aiCostUsd().toPlainString(), baseUrl, month)));
            }
            sent++;
        }
        return sent;
    }
}
