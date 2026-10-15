package ai.zamu.conversations;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ai.zamu.shared.jobs.Job;
import ai.zamu.shared.jobs.JobHandler;
import ai.zamu.shared.mail.EmailMessage;
import ai.zamu.shared.mail.EmailSender;
import ai.zamu.users.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * Job {@code emergency_alert}: NOT-02, instant alert to the owners when a customer describes an emergency. By email
 * until the owner-alert channel is decided (spec open question: WhatsApp alerts). The email holds a link only, no
 * health details (NFR-11).
 */
@Component
class EmergencyAlertHandler implements JobHandler {

    private final UserService users;
    private final EmailSender email;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    EmergencyAlertHandler(UserService users, EmailSender email, ObjectMapper objectMapper,
            @Value("${zamu.base-url}") String baseUrl) {
        this.users = users;
        this.email = email;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public String type() {
        return ConversationEngine.EMERGENCY_ALERT_JOB;
    }

    @Override
    public void handle(Job job) {
        UUID conversationId = UUID.fromString(objectMapper.readTree(job.payloadJson()).path("conversationId").asString(""));
        for (String owner : users.activeOwnerEmails(job.tenantId())) {
            email.send(new EmailMessage(owner, "Urgent: a customer may have an emergency", """
                    A customer's message on Zamu looks like an emergency. Zamu gave them the emergency instruction and
                    handed the chat to your team.

                    Open the chat now: %s/app/conversations/%s

                    Zamu""".formatted(baseUrl, conversationId)));
        }
    }
}
