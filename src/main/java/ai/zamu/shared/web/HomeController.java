package ai.zamu.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The tenant dashboard home. The public home page at "/" is in the leads package (LandingController). */
@Controller
class HomeController {

    /** The inbox is the dashboard home (spec: Web routes). */
    @GetMapping("/app")
    String appHome() {
        return "redirect:/app/inbox";
    }
}
