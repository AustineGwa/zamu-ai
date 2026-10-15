package ai.zamu.bookings;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.AdminTenantScope;
import ai.zamu.tenants.TenantService;

/**
 * Setup wizard, services step: services, the staff or rooms that deliver them, and booking rules
 * (spec: Tenant setup wizard "services and resources"; BK-02, BK-04, BK-08, BK-11).
 */
@Controller
@RequestMapping("/admin/tenants/{tenantId}/setup/services")
class ServicesSetupController {

    private final BookingSetupService setup;
    private final TenantService tenants;
    private final AdminTenantScope scope;

    ServicesSetupController(BookingSetupService setup, TenantService tenants, AdminTenantScope scope) {
        this.setup = setup;
        this.tenants = tenants;
        this.scope = scope;
    }

    @GetMapping
    String page(@PathVariable UUID tenantId, Model model) {
        TenantId id = scope.forPath(tenantId);
        if (!model.containsAttribute("serviceForm")) {
            model.addAttribute("serviceForm", ServiceForm.empty());
        }
        if (!model.containsAttribute("resourceForm")) {
            model.addAttribute("resourceForm", ResourceForm.empty());
        }
        if (!model.containsAttribute("settingsForm")) {
            model.addAttribute("settingsForm", SettingsForm.from(setup.settings(id)));
        }
        addModel(id, model);
        return "bookings/setup-services";
    }

    @PostMapping
    String createService(@PathVariable UUID tenantId, @Validated @ModelAttribute("serviceForm") ServiceForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (!errors.hasErrors()) {
            try {
                setup.createService(id, form);
                redirect.addFlashAttribute("success", "Service added.");
                return redirectTo(tenantId);
            } catch (BookingRuleException e) {
                errors.reject("service", e.getMessage());
            }
        }
        model.addAttribute("resourceForm", ResourceForm.empty());
        model.addAttribute("settingsForm", SettingsForm.from(setup.settings(id)));
        model.addAttribute("openServiceForm", true);
        addModel(id, model);
        return "bookings/setup-services";
    }

    @GetMapping("/{serviceId}")
    String edit(@PathVariable UUID tenantId, @PathVariable UUID serviceId, Model model) {
        TenantId id = scope.forPath(tenantId);
        if (!model.containsAttribute("serviceForm")) {
            model.addAttribute("serviceForm", ServiceForm.from(setup.service(id, serviceId)));
        }
        model.addAttribute("serviceId", serviceId);
        addModel(id, model);
        return "bookings/setup-service-edit";
    }

    @PostMapping("/{serviceId}")
    String update(@PathVariable UUID tenantId, @PathVariable UUID serviceId,
            @Validated @ModelAttribute("serviceForm") ServiceForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (!errors.hasErrors()) {
            try {
                setup.updateService(id, serviceId, form);
                redirect.addFlashAttribute("success", "Service saved.");
                return redirectTo(tenantId);
            } catch (BookingRuleException e) {
                errors.reject("service", e.getMessage());
            }
        }
        model.addAttribute("serviceId", serviceId);
        addModel(id, model);
        return "bookings/setup-service-edit";
    }

    @PostMapping("/resources")
    String createResource(@PathVariable UUID tenantId, @Validated @ModelAttribute("resourceForm") ResourceForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (errors.hasErrors()) {
            model.addAttribute("serviceForm", ServiceForm.empty());
            model.addAttribute("settingsForm", SettingsForm.from(setup.settings(id)));
            addModel(id, model);
            return "bookings/setup-services";
        }
        setup.createResource(id, form);
        redirect.addFlashAttribute("success", "Added " + form.name().strip() + ".");
        return redirectTo(tenantId);
    }

    @PostMapping("/resources/{resourceId}")
    String updateResource(@PathVariable UUID tenantId, @PathVariable UUID resourceId,
            @Validated ResourceForm form, BindingResult errors, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("error", "Enter a name for the staff member or room.");
        } else {
            setup.updateResource(id, resourceId, form);
            redirect.addFlashAttribute("success", "Saved " + form.name().strip() + ".");
        }
        return redirectTo(tenantId);
    }

    @PostMapping("/settings")
    String saveSettings(@PathVariable UUID tenantId, @Validated @ModelAttribute("settingsForm") SettingsForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (errors.hasErrors()) {
            model.addAttribute("serviceForm", ServiceForm.empty());
            model.addAttribute("resourceForm", ResourceForm.empty());
            addModel(id, model);
            return "bookings/setup-services";
        }
        setup.saveSettings(id, form.toSettings());
        redirect.addFlashAttribute("success", "Booking rules saved.");
        return redirectTo(tenantId);
    }

    private void addModel(TenantId id, Model model) {
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("step", "services");
        model.addAttribute("services", setup.allServices(id));
        model.addAttribute("resources", setup.allResources(id));
    }

    private static String redirectTo(UUID tenantId) {
        return "redirect:/admin/tenants/" + tenantId + "/setup/services";
    }
}
