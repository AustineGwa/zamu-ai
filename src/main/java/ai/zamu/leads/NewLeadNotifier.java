package ai.zamu.leads;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ai.zamu.shared.jobs.Job;
import ai.zamu.shared.jobs.JobHandler;
import ai.zamu.shared.mail.EmailMessage;
import ai.zamu.shared.mail.EmailSender;
import ai.zamu.shared.time.ZamuTime;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Emails the Zamu team about each new sign-up (ZAMU_LEADS_NOTIFY_EMAIL). Does nothing if no address is set. */
@Component
class NewLeadNotifier implements JobHandler {

    private final LeadRepository leads;
    private final EmailSender email;
    private final ObjectMapper objectMapper;
    private final String notifyEmail;
    private final String baseUrl;

    NewLeadNotifier(LeadRepository leads, EmailSender email, ObjectMapper objectMapper,
            @Value("${zamu.leads.notify-email:}") String notifyEmail, @Value("${zamu.base-url}") String baseUrl) {
        this.leads = leads;
        this.email = email;
        this.objectMapper = objectMapper;
        this.notifyEmail = notifyEmail;
        this.baseUrl = baseUrl;
    }

    @Override
    public String type() {
        return LeadService.NOTIFY_JOB;
    }

    @Override
    public void handle(Job job) {
        if (notifyEmail == null || notifyEmail.isBlank()) {
            return;
        }
        JsonNode payload = objectMapper.readTree(job.payloadJson());
        Lead lead = leads.findById(UUID.fromString(payload.get("leadId").asString())).orElse(null);
        if (lead == null) {
            return;
        }
        String body = """
                New sign-up on the Zamu website

                Business:  %s (%s)
                Contact:   %s
                Phone:     %s
                Email:     %s
                Received:  %s

                %s

                Open the leads list: %s/admin/leads
                """.formatted(lead.businessName(), lead.businessType().label(), lead.contactName(),
                lead.toView().phoneDisplay(), lead.email() == null ? "not given" : lead.email(),
                ZamuTime.display(lead.createdAt()), lead.message() == null ? "" : lead.message(), baseUrl);
        email.send(new EmailMessage(notifyEmail, "New Zamu sign-up: " + lead.businessName(), body));
    }
}
