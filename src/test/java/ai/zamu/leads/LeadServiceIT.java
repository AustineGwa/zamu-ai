package ai.zamu.leads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.jobs.Job;
import ai.zamu.support.AbstractMySqlIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = "zamu.leads.notify-email=sales@zamu.test")
class LeadServiceIT extends AbstractMySqlIT {

    @Autowired
    LeadService leads;

    @Autowired
    NewLeadNotifier notifier;

    @Test
    void PAY_04_storesTheSignUpWithANormalisedPhoneAndQueuesTheEmail() {
        LeadView lead = leads.signUp(form("0712 345 678"));

        assertThat(lead.phone()).isEqualTo("254712345678");
        assertThat(leads.recent(LeadStatus.NEW)).extracting(LeadView::businessName).containsExactly("Afya Bora Clinic");
        String payload = jdbc.sql("SELECT payload FROM jobs WHERE type = 'notify_new_lead'").query(String.class).single();
        assertThat(payload).as("only the id goes in the job (NFR-13)").contains(lead.id().toString()).doesNotContain("0712");
    }

    @Test
    void rejectsNumbersThatAreNotKenyanMobiles() {
        assertThatThrownBy(() -> leads.signUp(form("+44 7700 900123"))).isInstanceOf(InvalidPhoneException.class);
        assertThat(leads.recent(null)).isEmpty();
    }

    @Test
    void theTeamIsEmailedAboutTheSignUp() {
        LeadView lead = leads.signUp(form("0712 345 678"));

        notifier.handle(new Job(UuidV7.next(), LeadService.NOTIFY_JOB, null,
                "{\"leadId\":\"" + lead.id() + "\"}", 1, 5, null));

        assertThat(emails.sentTo("sales@zamu.test")).singleElement()
                .satisfies(m -> assertThat(m.textBody()).contains("Afya Bora Clinic", "0712 345 678"));
    }

    @Test
    void NFR_14_statusChangesAreAudited() {
        LeadView lead = leads.signUp(form("0712 345 678"));
        leads.changeStatus(lead.id(), LeadStatus.CONTACTED);

        assertThat(leads.recent(LeadStatus.CONTACTED)).hasSize(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_logs WHERE action = 'lead.status_changed'").query(Integer.class).single())
                .isEqualTo(1);
    }

    private static SignUpForm form(String phone) {
        return new SignUpForm("Afya Bora Clinic", "Jane Wanjiku", phone, "", BusinessType.CLINIC,
                "Busy after 6pm", true, "");
    }
}
