package ai.zamu.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The tenant dashboard home. The public home page at "/" is in the leads package (LandingController). */
@Controller
class HomeController {

    /** Placeholder dashboard home until the inbox lands in Milestone 2. */
    @GetMapping("/app")
    String appHome() {
        return "shared/app-home";
    }
}
