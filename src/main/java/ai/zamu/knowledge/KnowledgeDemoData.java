package ai.zamu.knowledge;

import java.util.List;

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
 * Local "h2" profile only: a small approved knowledge base for the demo clinic, so the WhatsApp simulator gives
 * real answers straight away. Invented example content.
 */
@Component
@Profile("h2")
@ConditionalOnProperty(name = "zamu.local-demo.enabled", havingValue = "true")
@Order(300)
class KnowledgeDemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeDemoData.class);

    private final TenantService tenants;
    private final KnowledgeService knowledge;

    KnowledgeDemoData(TenantService tenants, KnowledgeService knowledge) {
        this.tenants = tenants;
        this.knowledge = knowledge;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantView demo = tenants.listAll().stream().filter(t -> "Demo Clinic".equals(t.displayName())).findFirst()
                .orElse(null);
        if (demo == null) {
            return;
        }
        TenantId id = TenantId.of(demo.id());
        if (!knowledge.list(id).isEmpty()) {
            return;
        }
        List<KbEntryForm> entries = List.of(
                new KbEntryForm("Opening hours", "What time do you open?",
                        "We are open Monday to Friday 8:00am to 6:00pm and Saturday 9:00am to 1:00pm. We are closed on Sundays and public holidays.",
                        "Tunafungua Jumatatu hadi Ijumaa saa 2:00 asubuhi hadi saa 12:00 jioni, na Jumamosi saa 3:00 asubuhi hadi saa 7:00 mchana. Tunafunga Jumapili na sikukuu.",
                        "Demo data"),
                new KbEntryForm("Consultation fee", "How much is a consultation?",
                        "A general consultation costs KES 2,000. A dental check-up costs KES 2,500.",
                        "Kumwona daktari ni KES 2,000. Uchunguzi wa meno ni KES 2,500.", "Demo data"),
                new KbEntryForm("Filling prices", "How much is a filling?",
                        "A filling costs KES 4,500 to 8,000 depending on its size. The dentist confirms the price at your visit.",
                        "Kuziba jino ni KES 4,500 hadi 8,000 kulingana na ukubwa. Daktari atathibitisha bei ukifika.",
                        "Demo data"),
                new KbEntryForm("Location", "Where are you located?",
                        "We are on Ngong Road, Adams Arcade, first floor, opposite the petrol station. Parking is free.",
                        "Tuko Ngong Road, Adams Arcade, ghorofa ya kwanza, mkabala na kituo cha mafuta. Maegesho ni bure.",
                        "Demo data"),
                new KbEntryForm("Insurance", "Do you accept insurance or SHA?",
                        "We accept SHA and most major insurance covers. Bring your insurance card and ID to every visit.",
                        "Tunapokea SHA na bima nyingi kubwa. Leta kadi ya bima na kitambulisho kila unapokuja.",
                        "Demo data"));
        for (KbEntryForm form : entries) {
            knowledge.approve(id, knowledge.create(id, form, null), null);
        }
        log.info("Local demo data: {} approved knowledge base entries for Demo Clinic", entries.size());
    }
}
