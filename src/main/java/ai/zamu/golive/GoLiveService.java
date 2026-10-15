package ai.zamu.golive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.bookings.BookingSetupService;
import ai.zamu.ai.AnswerService;
import ai.zamu.ai.EvalRunView;
import ai.zamu.ai.EvaluationService;
import ai.zamu.bookings.BusinessHoursService;
import ai.zamu.knowledge.KnowledgeService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.tenants.IllegalTenantStatusException;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;
import ai.zamu.users.UserService;
import ai.zamu.users.UserView;
import ai.zamu.whatsapp.TemplateService;
import ai.zamu.whatsapp.WhatsAppAccountService;
import ai.zamu.whatsapp.WhatsAppAccountView;

/**
 * Setup wizard, go-live step (spec: Tenant setup wizard, "go-live checklist"; Milestone 4, pilot release). Checks
 * what can be checked automatically; the phone tests with the client and the client's written approval of the
 * knowledge base stay manual (Operations Manual). ASSUMPTION: which items are required versus recommended.
 */
@Service
public class GoLiveService {

    private final TenantService tenants;
    private final WhatsAppAccountService whatsapp;
    private final TemplateService templates;
    private final BusinessHoursService hours;
    private final KnowledgeService knowledge;
    private final EvaluationService evaluation;
    private final AnswerService answers;
    private final UserService users;
    private final BookingSetupService bookingSetup;

    GoLiveService(TenantService tenants, WhatsAppAccountService whatsapp, TemplateService templates,
            BusinessHoursService hours, KnowledgeService knowledge, EvaluationService evaluation, AnswerService answers,
            UserService users, BookingSetupService bookingSetup) {
        this.tenants = tenants;
        this.whatsapp = whatsapp;
        this.templates = templates;
        this.hours = hours;
        this.knowledge = knowledge;
        this.evaluation = evaluation;
        this.answers = answers;
        this.users = users;
        this.bookingSetup = bookingSetup;
    }

    @Transactional(readOnly = true)
    public List<GoLiveCheck> checks(TenantId id) {
        TenantView tenant = tenants.get(id);
        List<GoLiveCheck> checks = new ArrayList<>();
        Optional<WhatsAppAccountView> account = whatsapp.view(id);
        checks.add(new GoLiveCheck("WhatsApp number connected", account.isPresent(), true,
                account.map(WhatsAppAccountView::displayPhone).orElse("Not connected"), "setup/whatsapp"));
        boolean receiving = account.map(WhatsAppAccountView::webhookVerified).orElse(false);
        checks.add(new GoLiveCheck("Messages are arriving from Meta", receiving, true,
                receiving ? "A webhook from Meta has been received" : "Send a test message to the number",
                "setup/whatsapp"));
        boolean live = answers.liveModel();
        checks.add(new GoLiveCheck("AI provider configured", live, true,
                live ? "Using the configured model" : "Offline stand-in only: set the provider and API key", null));
        int approved = knowledge.approvedCount(id);
        checks.add(new GoLiveCheck("Knowledge base approved", approved > 0, true,
                approved + " approved entries (the Operations Manual expects the top 20 questions at least)", "kb"));
        int cases = evaluation.caseCount(id);
        checks.add(new GoLiveCheck("Evaluation set of " + EvaluationService.MINIMUM_CASES + " questions",
                cases >= EvaluationService.MINIMUM_CASES, true, cases + " questions", "eval"));
        Optional<EvalRunView> run = evaluation.latestFinishedRun(id);
        checks.add(new GoLiveCheck("Latest test run: 95% correct, no trap answered",
                run.map(EvalRunView::meetsBar).orElse(false), true,
                run.map(r -> r.percent() + "% correct, " + r.trapFailures() + " trap failures").orElse("No finished run"),
                "eval"));
        long activeUsers = users.listForTenant(id).stream().filter(UserView::active).count();
        checks.add(new GoLiveCheck("Staff can sign in to the inbox", activeUsers > 0, true,
                activeUsers + " active users", "setup/staff"));
        boolean hoursSet = hours.configured(id);
        checks.add(new GoLiveCheck("Opening and handover hours set", hoursSet, false,
                hoursSet ? "Set" : "Not set: handovers are treated as always staffed", "setup/hours"));
        boolean emergency = tenant.emergencyNumber() != null;
        checks.add(new GoLiveCheck("Emergency number set", emergency, false,
                emergency ? tenant.emergencyNumber() : "Not set: the emergency reply uses 999 or 112", "setup/tone"));
        // Milestone 5: bookings are recommended, not required (a tenant can go live for answers only).
        long services = bookingSetup.allServices(id).stream().filter(s -> s.active()).count();
        checks.add(new GoLiveCheck("Bookable services", services > 0, false,
                services > 0 ? services + " services customers can book" : "None: booking requests go to staff",
                "setup/services"));
        BookingSetupService.CalendarStatus calendar = bookingSetup.calendarStatus(id);
        checks.add(new GoLiveCheck("Google Calendar connected", calendar.connected(), false,
                calendar.connected() ? "Checked before every booking"
                        : calendar.revoked() ? "Access was revoked: reconnect" : "Not connected: slots use opening hours and Zamu's bookings only",
                "setup/calendar"));
        boolean template = !templates.approved(id).isEmpty();
        checks.add(new GoLiveCheck("An approved message template", template, false,
                template ? "Staff can reach customers after 24 hours" : "Staff can't message after the 24-hour window",
                "templates"));
        return checks;
    }

    public boolean ready(List<GoLiveCheck> checks) {
        return checks.stream().filter(GoLiveCheck::required).allMatch(GoLiveCheck::passed);
    }

    /** @throws IllegalStateException when a required check fails */
    @Transactional
    public TenantView goLive(TenantId id) {
        if (!ready(checks(id))) {
            throw new IllegalStateException("Some required checks haven't passed yet.");
        }
        try {
            return tenants.goLive(id);
        } catch (IllegalTenantStatusException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    @Transactional
    public TenantView pause(TenantId id) {
        try {
            return tenants.pause(id);
        } catch (IllegalTenantStatusException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }
}
