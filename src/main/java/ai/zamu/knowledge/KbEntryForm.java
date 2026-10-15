package ai.zamu.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin console: create or edit a knowledge base entry (Operations Manual, Building the knowledge base). */
public record KbEntryForm(
        @NotBlank(message = "Enter a short title") @Size(max = 200) String title,
        @NotBlank(message = "Enter the question as customers ask it") @Size(max = 500) String question,
        @NotBlank(message = "Enter the answer in English") @Size(max = 4000, message = "Keep the answer under 4,000 characters") String answerEn,
        @Size(max = 4000, message = "Keep the answer under 4,000 characters") String answerSw,
        @Size(max = 300) String sourceRef) {

    static KbEntryForm empty() {
        return new KbEntryForm("", "", "", "", "");
    }

    static KbEntryForm from(KbEntryView e) {
        return new KbEntryForm(e.title(), e.question(), e.answerEn(), e.answerSw() == null ? "" : e.answerSw(),
                e.sourceRef() == null ? "" : e.sourceRef());
    }
}
