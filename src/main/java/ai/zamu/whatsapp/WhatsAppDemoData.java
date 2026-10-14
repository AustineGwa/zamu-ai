package ai.zamu.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;

/**
 * Local "h2" profile only: connects the demo business to a pretend WhatsApp number and adds an approved
 * "follow_up" template, so the inbox and the simulator at /dev/whatsapp work straight away.
 */
@Component
@Profile("h2")
@ConditionalOnProperty(name = "zamu.local-demo.enabled", havingValue = "true")
@Order(200) // after LocalDemoData
class WhatsAppDemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppDemoData.class);

    private final TenantService tenants;
    private final WhatsAppAccountService accounts;
    private final TemplateService templates;

    WhatsAppDemoData(TenantService tenants, WhatsAppAccountService accounts, TemplateService templates) {
        this.tenants = tenants;
        this.accounts = accounts;
        this.templates = templates;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantView demo = tenants.listAll().stream()
                .filter(t -> "Demo Clinic".equals(t.displayName()))
                .findFirst().orElse(null);
        if (demo == null) {
            return;
        }
        TenantId id = TenantId.of(demo.id());
        if (accounts.view(id).isPresent()) {
            return;
        }
        accounts.connect(id, new WhatsAppConnectForm("100000000000001", "100000000000002", "+254 700 000 000",
                "local-dev-token"));
        var templateId = templates.create(id, new WaTemplateForm("follow_up", "utility", "en",
                "Hello {{1}}, this is Demo Clinic following up on your message. Reply here and we will help."));
        templates.recordStatus(id, templateId, WaTemplateStatus.APPROVED, null);
        log.info("Local demo data: Demo Clinic WhatsApp connected; try the customer simulator at /dev/whatsapp");
    }
}
