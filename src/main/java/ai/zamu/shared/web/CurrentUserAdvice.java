package ai.zamu.shared.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import ai.zamu.shared.security.CurrentUser;
import ai.zamu.shared.security.ZamuPrincipal;

/** Makes the logged-in user available to every page layout as {@code currentUser}. */
@ControllerAdvice
class CurrentUserAdvice {

    @ModelAttribute("currentUser")
    ZamuPrincipal currentUser() {
        return CurrentUser.get().orElse(null);
    }
}
