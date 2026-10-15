package ai.zamu.privacy;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.conversations.CustomerSummary;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.phone.KenyanPhone;
import ai.zamu.shared.security.AdminTenantScope;
import ai.zamu.tenants.IllegalTenantStatusException;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;

/**
 * Admin console, privacy and data for one client (NFR-11, NFR-12): retention settings, customer export and
 * deletion on request, and offboarding. The tenant comes from the path and is checked by {@link AdminTenantScope}.
 */
@Controller
class PrivacyController {

    private final PrivacyService privacy;
    private final TenantService tenants;
    private final AdminTenantScope scope;

    PrivacyController(PrivacyService privacy, TenantService tenants, AdminTenantScope scope) {
        this.privacy = privacy;
        this.tenants = tenants;
        this.scope = scope;
    }

    @GetMapping("/admin/tenants/{tenantId}/privacy")
    String page(@PathVariable UUID tenantId, @RequestParam(required = false) String phone, Model model) {
        TenantId id = scope.forPath(tenantId);
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("step", "privacy");
        model.addAttribute("retention", privacy.settings(id));
        model.addAttribute("phone", phone);
        if (phone != null && !phone.isBlank()) {
            String normalised = KenyanPhone.normalise(phone).orElse(phone.replaceAll("\\D", ""));
            CustomerSummary customer = privacy.findCustomer(id, normalised).orElse(null);
            model.addAttribute("customer", customer);
            model.addAttribute("searched", true);
        }
        return "privacy/privacy";
    }

    @PostMapping("/admin/tenants/{tenantId}/privacy/retention")
    String retention(@PathVariable UUID tenantId, @RequestParam int conversationMonths,
            @RequestParam int offboardPurgeDays, RedirectAttributes redirect) {
        try {
            privacy.saveSettings(scope.forPath(tenantId), new RetentionSettings(conversationMonths, offboardPurgeDays));
            redirect.addFlashAttribute("success", "Retention saved.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "Keep conversations 1 to 120 months, and offboarded data 1 to 365 days.");
        }
        return "redirect:/admin/tenants/" + tenantId + "/privacy";
    }

    @GetMapping("/admin/tenants/{tenantId}/privacy/customers/{customerId}/export")
    ResponseEntity<byte[]> export(@PathVariable UUID tenantId, @PathVariable UUID customerId) {
        byte[] json = privacy.exportCustomer(scope.forPath(tenantId), customerId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"customer-" + customerId + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    /** Deletion needs the phone number typed again, so the wrong customer isn't deleted by a slip. */
    @PostMapping("/admin/tenants/{tenantId}/privacy/customers/{customerId}/delete")
    String delete(@PathVariable UUID tenantId, @PathVariable UUID customerId, @RequestParam String confirmPhone,
            RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        String normalised = KenyanPhone.normalise(confirmPhone).orElse(confirmPhone.replaceAll("\\D", ""));
        CustomerSummary customer = privacy.findCustomer(id, normalised).orElse(null);
        if (customer == null || !customer.id().equals(customerId)) {
            redirect.addFlashAttribute("error", "The phone number doesn't match this customer. Nothing was deleted.");
            return "redirect:/admin/tenants/" + tenantId + "/privacy";
        }
        privacy.deleteCustomer(id, customerId);
        redirect.addFlashAttribute("success", "The customer and their data were deleted (payment records are kept).");
        return "redirect:/admin/tenants/" + tenantId + "/privacy";
    }

    @PostMapping("/admin/tenants/{tenantId}/privacy/offboard")
    String offboard(@PathVariable UUID tenantId, @RequestParam String confirmName, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        TenantView tenant = tenants.get(id);
        if (!tenant.displayName().equalsIgnoreCase(confirmName == null ? "" : confirmName.strip())) {
            redirect.addFlashAttribute("error", "Type the business name exactly to offboard.");
            return "redirect:/admin/tenants/" + tenantId + "/privacy";
        }
        try {
            tenants.offboard(id);
            redirect.addFlashAttribute("success", "Offboarded. Data will be deleted after "
                    + privacy.settings(id).offboardPurgeDays() + " days.");
        } catch (IllegalTenantStatusException e) {
            redirect.addFlashAttribute("error", "Pause Zamu for this client first (Go-live step).");
        }
        return "redirect:/admin/tenants/" + tenantId + "/privacy";
    }
}
