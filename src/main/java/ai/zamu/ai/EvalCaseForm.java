package ai.zamu.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Admin console: add a question to the tenant's evaluation set. */
public record EvalCaseForm(
        @NotNull(message = "Choose a type") EvalCaseKind kind,
        @NotBlank @Pattern(regexp = "en|sw", message = "Choose English or Swahili") String language,
        @NotBlank(message = "Enter the question") @Size(max = 500) String question,
        @Size(max = 1000) String expectedAnswer,
        @Size(max = 500) String mustInclude) {

    static EvalCaseForm empty() {
        return new EvalCaseForm(EvalCaseKind.ANSWER, "en", "", "", "");
    }
}
