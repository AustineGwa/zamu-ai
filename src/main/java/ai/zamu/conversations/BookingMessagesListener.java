package ai.zamu.conversations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ai.zamu.bookings.Booking;
import ai.zamu.bookings.BookingEvents;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;

/**
 * Tells the customer about booking changes they didn't make in the chat: staff bookings and confirmations
 * (BK-07, BK-09), cancellations and moves by staff or the calendar (BK-10), and reminders (BK-11).
 *
 * <p>Inside the 24-hour window the message goes as text (with buttons where a reply is expected). Outside it, a
 * template is needed (MSG-14): reminders use the approved {@code booking_reminder} template; other notices are
 * skipped and logged, since staff made the change and can call the customer. ASSUMPTION: template name and
 * parameters ({{1}} business name, {{2}} day and time) agreed with Meta per tenant.
 */
@Component
class BookingMessagesListener {

    static final String REMINDER_TEMPLATE = "booking_reminder";
    private static final Logger log = LoggerFactory.getLogger(BookingMessagesListener.class);

    private final CustomerRepository customers;
    private final ConversationRepository conversations;
    private final Outbox outbox;
    private final ObjectProvider<TemplateCatalog> templates;
    private final TenantService tenants;
    private final Clock clock;

    BookingMessagesListener(CustomerRepository customers, ConversationRepository conversations, Outbox outbox,
            ObjectProvider<TemplateCatalog> templates, TenantService tenants, Clock clock) {
        this.customers = customers;
        this.conversations = conversations;
        this.outbox = outbox;
        this.templates = templates;
        this.tenants = tenants;
        this.clock = clock;
    }

    @EventListener
    void on(BookingEvents.Confirmed event) {
        if (event.notifyCustomer()) {
            send(event.booking(), (b, start, lang) -> BookingWording.confirmed(b, start, lang), true);
        }
    }

    @EventListener
    void on(BookingEvents.Cancelled event) {
        if (!event.notifyCustomer()) {
            return;
        }
        boolean unpaid = "system".equals(event.by());
        send(event.booking(), (b, start, lang) -> unpaid ? BookingWording.unpaidReleased(b, start, lang)
                : BookingWording.cancelledByBusiness(b, start, lang), false);
    }

    @EventListener
    void on(BookingEvents.Moved event) {
        if (event.notifyCustomer()) {
            send(event.booking(), (b, start, lang) -> BookingWording.moved(b, start, lang), true);
        }
    }

    /** BK-11, BK-12: no reminder for customers who opted out. */
    @EventListener
    void on(BookingEvents.ReminderDue event) {
        Booking b = event.booking();
        TenantId tenantId = TenantId.of(b.tenantId());
        Optional<Customer> customer = customers.findById(tenantId, b.customerId());
        if (customer.isEmpty() || customer.get().optedOutAt() != null || customer.get().blocked()) {
            return;
        }
        Optional<Conversation> conversation = conversations.findLatest(tenantId, b.customerId(), Channel.WHATSAPP);
        if (conversation.isEmpty()) {
            return;
        }
        TenantView tenant = tenants.get(tenantId);
        ZoneId zone = ZoneId.of(tenant.timezone());
        ZonedDateTime start = b.startsAt().atZone(zone);
        boolean tomorrow = start.toLocalDate().equals(LocalDate.now(clock.withZone(zone)).plusDays(1));
        String language = language(customer.get());
        String body = BookingWording.reminder(tenant.displayName(), start, tomorrow, language);
        Conversation c = conversation.get();
        if (c.windowOpen(clock.instant())) {
            outbox.interactive(tenantId, c, body, Interactive.buttons(List.of(
                    new Interactive.Choice("bk:y:" + b.id(), "sw".equals(language) ? "Thibitisha" : "Confirm", null),
                    new Interactive.Choice("bk:r:" + b.id(), BookingWording.rescheduleLabel(language), null),
                    new Interactive.Choice("bk:x:" + b.id(), BookingWording.cancelLabel(language), null))));
            return;
        }
        TemplateCatalog catalog = templates.getIfAvailable();
        Optional<TemplateCatalog.TemplateOption> template = catalog == null ? Optional.empty()
                : catalog.find(tenantId, REMINDER_TEMPLATE, language).or(() -> catalog.find(tenantId, REMINDER_TEMPLATE, "en"));
        if (template.isEmpty()) {
            log.info("No approved {} template for tenant {}; reminder for booking {} not sent", REMINDER_TEMPLATE,
                    tenantId, b.id());
            return;
        }
        List<String> params = List.of(tenant.displayName(), BookingWording.when(start, template.get().language()));
        outbox.botTemplate(tenantId, c, new TemplateRef(template.get().name(), template.get().language(), params),
                template.get().render(params));
    }

    private interface Text {
        String of(Booking b, ZonedDateTime start, String language);
    }

    private void send(Booking b, Text text, boolean withChangeButtons) {
        TenantId tenantId = TenantId.of(b.tenantId());
        Optional<Customer> customer = customers.findById(tenantId, b.customerId());
        if (customer.isEmpty() || customer.get().blocked()) {
            return;
        }
        Optional<Conversation> conversation = conversations.findLatest(tenantId, b.customerId(), Channel.WHATSAPP);
        Instant now = clock.instant();
        if (conversation.isEmpty() || !conversation.get().windowOpen(now)) {
            log.info("Booking {} changed outside the 24-hour window; the customer was not messaged", b.id());
            return;
        }
        String language = language(customer.get());
        ZonedDateTime start = b.startsAt().atZone(ZoneId.of(tenants.get(tenantId).timezone()));
        String body = text.of(b, start, language);
        if (withChangeButtons) {
            outbox.interactive(tenantId, conversation.get(), body, Interactive.buttons(List.of(
                    new Interactive.Choice("bk:r:" + b.id(), BookingWording.rescheduleLabel(language), null),
                    new Interactive.Choice("bk:x:" + b.id(), BookingWording.cancelLabel(language), null))));
        } else {
            outbox.text(tenantId, conversation.get(), MessageSender.BOT, null, body);
        }
    }

    private static String language(Customer customer) {
        return "sw".equals(customer.language()) ? "sw" : "en";
    }
}
