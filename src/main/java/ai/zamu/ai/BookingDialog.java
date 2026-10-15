package ai.zamu.ai;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ai.zamu.bookings.BookableService;
import ai.zamu.bookings.Booking;
import ai.zamu.bookings.BookingRuleException;
import ai.zamu.bookings.BookingService;
import ai.zamu.bookings.BookingSlotTakenException;
import ai.zamu.bookings.BookingStatus;
import ai.zamu.bookings.CalendarUnavailableException;
import ai.zamu.bookings.Slot;
import ai.zamu.conversations.AnswerProvider.Answer;
import ai.zamu.conversations.AnswerProvider.Question;
import ai.zamu.conversations.BookingWording;
import ai.zamu.conversations.Interactive;
import ai.zamu.handovers.HandoverReason;
import ai.zamu.knowledge.Tokens;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.web.NotFoundException;
import ai.zamu.tenants.TenantService;

/**
 * Booking by chat (BK-03, BK-05, BK-06, BK-08): choose a service, tap one of the nearest free times, and the
 * booking is confirmed (or waits for its deposit); reschedule and cancel the same way. Every step is a tapped
 * option whose ID carries what was chosen, so nothing is remembered between messages except the bookings
 * themselves. IDs come back from the customer's phone, so each one is checked again: the service and booking
 * must belong to this tenant (and the booking to this customer), and the time must still be free.
 *
 * <p>Option IDs: {@code bk:s:<service>} service; {@code bk:t:<service>:<resource|0>:<epoch>} book a time;
 * {@code bk:r:<booking>} reschedule; {@code bk:m:<booking>:<resource|0>:<epoch>} move to a time;
 * {@code bk:x:<booking>} ask to cancel; {@code bk:c:<booking>} cancel; {@code bk:k:<booking>} keep;
 * {@code bk:y:<booking>} confirm attendance (reminder).
 */
@Component
class BookingDialog {

    private static final Logger log = LoggerFactory.getLogger(BookingDialog.class);
    static final String PREFIX = "bk:";
    /** Spec BK-03 offers up to 5 slots. */
    static final int SLOTS = 5;
    private static final Set<String> CONFIRM_WORDS = Set.of("confirm", "thibitisha");
    private static final Set<String> RESCHEDULE_WORDS = Set.of("reschedule", "badilisha", "badilisha muda");
    private static final Set<String> CANCEL_WORDS = Set.of("cancel", "ghairi");

    private final BookingService bookings;
    private final TenantService tenants;

    BookingDialog(BookingService bookings, TenantService tenants) {
        this.bookings = bookings;
        this.tenants = tenants;
    }

    /**
     * A tapped booking option, or a numbered or one-word reply to a reminder or confirmation. Empty when the
     * message isn't one of these.
     */
    Optional<Answer> reply(Question q) {
        if (q.customerId() == null) {
            return Optional.empty();
        }
        String id = q.replyId();
        if (id == null || !id.startsWith(PREFIX)) {
            id = awaitingReplyId(q);
        }
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(handle(q, id.split(":")));
        } catch (NotFoundException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            log.info("Ignored a booking option that doesn't match this customer");
            return Optional.of(Answer.reply(BookingWording.noUpcoming(q.language()), null, List.of()));
        } catch (CalendarUnavailableException e) {
            return Optional.of(Answer.handover(HandoverReason.BOOKING));
        }
    }

    /** The customer asked to book, reschedule or cancel in their own words (MSG-07 intents). */
    Answer start(Question q, String intent) {
        if (q.customerId() == null) {
            return Answer.handover(HandoverReason.BOOKING); // admin preview and evaluation: no customer
        }
        try {
            return switch (intent) {
                case "book" -> startBooking(q);
                case "reschedule", "cancel" -> startChange(q, "reschedule".equals(intent));
                default -> Answer.handover(HandoverReason.BOOKING);
            };
        } catch (CalendarUnavailableException e) {
            return Answer.handover(HandoverReason.BOOKING);
        }
    }

    private Answer startBooking(Question q) {
        TenantId t = q.tenantId();
        List<BookableService> services = bookings.services(t);
        if (services.isEmpty()) {
            return Answer.handover(HandoverReason.BOOKING);
        }
        BookableService match = services.size() == 1 ? services.get(0) : bestMatch(services, q.text());
        if (match != null) {
            return offerTimes(q, match, null, null);
        }
        String lang = q.language();
        List<Interactive.Choice> rows = new ArrayList<>();
        StringBuilder body = new StringBuilder(BookingWording.chooseService(lang));
        for (BookableService s : services.subList(0, Math.min(services.size(), Interactive.MAX_ROWS))) {
            String price = s.priceMin() == null ? null : BookingWording.money(s.priceMin())
                    + (s.priceMax() != null && s.priceMax().compareTo(s.priceMin()) > 0 ? " - " + BookingWording.money(s.priceMax()) : "");
            rows.add(new Interactive.Choice(PREFIX + "s:" + s.id(), s.name(lang), price));
            body.append('\n').append(rows.size()).append(". ").append(s.name(lang)).append(price == null ? "" : " (" + price + ")");
        }
        return Answer.choices(body.toString(), Interactive.list(BookingWording.chooseServiceButton(lang), rows));
    }

    /** The service the customer named, if exactly one service shares the most words with the message. */
    private static BookableService bestMatch(List<BookableService> services, String text) {
        Set<String> words = Tokens.of(text);
        BookableService best = null;
        int bestScore = 0;
        boolean tie = false;
        for (BookableService s : services) {
            Set<String> name = Tokens.of(s.name() + " " + (s.nameSw() == null ? "" : s.nameSw()));
            int score = (int) name.stream().filter(words::contains).count();
            if (score > bestScore) {
                best = s;
                bestScore = score;
                tie = false;
            } else if (score == bestScore && score > 0) {
                tie = true;
            }
        }
        return bestScore > 0 && !tie ? best : null;
    }

    private Answer startChange(Question q, boolean reschedule) {
        List<Booking> upcoming = bookings.upcomingForCustomer(q.tenantId(), q.customerId());
        String lang = q.language();
        if (upcoming.isEmpty()) {
            return Answer.reply(BookingWording.noUpcoming(lang), null, List.of());
        }
        if (upcoming.size() == 1) {
            return reschedule ? reschedule(q, upcoming.get(0)) : askCancel(q, upcoming.get(0));
        }
        ZoneId zone = zone(q.tenantId());
        List<Interactive.Choice> rows = new ArrayList<>();
        StringBuilder body = new StringBuilder(BookingWording.whichBooking(lang));
        for (Booking b : upcoming.subList(0, Math.min(upcoming.size(), Interactive.MAX_ROWS))) {
            ZonedDateTime start = b.startsAt().atZone(zone);
            rows.add(new Interactive.Choice(PREFIX + (reschedule ? "r:" : "x:") + b.id(),
                    BookingWording.rowTitle(start, lang), b.serviceName(lang)));
            body.append('\n').append(rows.size()).append(". ").append(b.serviceName(lang)).append(", ")
                    .append(BookingWording.when(start, lang));
        }
        return Answer.choices(body.toString(), Interactive.list(BookingWording.whichBooking(lang), rows));
    }

    private Answer handle(Question q, String[] p) {
        TenantId t = q.tenantId();
        return switch (p[1]) {
            case "s" -> offerTimes(q, service(t, p[2]), null, null);
            case "t" -> book(q, service(t, p[2]), resource(p[3]), Instant.ofEpochSecond(Long.parseLong(p[4])));
            case "r" -> reschedule(q, own(q, p[2]));
            case "m" -> move(q, own(q, p[2]), resource(p[3]), Instant.ofEpochSecond(Long.parseLong(p[4])));
            case "x" -> askCancel(q, own(q, p[2]));
            case "c" -> cancel(q, own(q, p[2]));
            case "k" -> Answer.reply(BookingWording.kept(q.language()), null, List.of());
            case "y" -> {
                Booking b = own(q, p[2]);
                bookings.clearAwaiting(t, b.id());
                yield Answer.reply(BookingWording.attendanceConfirmed(q.language()), null, List.of());
            }
            default -> throw new IllegalArgumentException("Unknown booking option");
        };
    }

    private Answer offerTimes(Question q, BookableService service, Booking moving, String intro) {
        String lang = q.language();
        List<Slot> slots = bookings.slots(q.tenantId(), service.id(), SLOTS, moving == null ? null : moving.id());
        if (slots.isEmpty()) {
            return Answer.handover(HandoverReason.BOOKING, BookingWording.noTimes(lang));
        }
        ZoneId zone = zone(q.tenantId());
        StringBuilder body = new StringBuilder(intro != null ? intro : BookingWording.chooseTime(service.name(lang), lang));
        List<Interactive.Choice> rows = new ArrayList<>();
        for (Slot s : slots) {
            ZonedDateTime start = s.startsAt().atZone(zone);
            String resource = s.resourceId() == null ? "0" : s.resourceId().toString();
            String id = moving == null
                    ? PREFIX + "t:" + service.id() + ":" + resource + ":" + s.startsAt().getEpochSecond()
                    : PREFIX + "m:" + moving.id() + ":" + resource + ":" + s.startsAt().getEpochSecond();
            rows.add(new Interactive.Choice(id, BookingWording.rowTitle(start, lang),
                    BookingWording.rowDescription(start, s.resourceName(), lang)));
            body.append('\n').append(rows.size()).append(". ").append(BookingWording.when(start, lang))
                    .append(s.resourceName() == null ? "" : ("sw".equals(lang) ? ", na " : ", with ") + s.resourceName());
        }
        return Answer.choices(body.toString(), Interactive.list(BookingWording.chooseTimeButton(lang), rows));
    }

    private Answer book(Question q, BookableService service, UUID resourceId, Instant start) {
        Booking booking;
        try {
            booking = bookings.hold(q.tenantId(), new BookingService.HoldRequest(q.customerId(), q.customerName(),
                    q.customerPhone(), q.conversationId(), service.id(), resourceId, start, "bot", null));
        } catch (BookingSlotTakenException e) {
            return offerTimes(q, service, null, BookingWording.slotTaken(q.language())); // BK-05
        }
        if (booking.status() == BookingStatus.CONFIRMED) {
            return confirmed(q, booking);
        }
        // BK-06: a deposit is needed. Until M-Pesa requests are switched on, staff arrange payment.
        return Answer.handover(HandoverReason.BOOKING, BookingWording.depositByStaff(booking.depositAmount(), q.language()));
    }

    private Answer reschedule(Question q, Booking b) {
        if (!bookings.changeAllowed(q.tenantId(), b)) {
            return Answer.handover(HandoverReason.BOOKING, BookingWording.pastCutoff(q.language())); // BK-08
        }
        BookableService service = bookings.service(q.tenantId(), b.serviceId())
                .orElseThrow(() -> new NotFoundException("Service not found"));
        return offerTimes(q, service, b, null);
    }

    private Answer move(Question q, Booking b, UUID resourceId, Instant start) {
        if (!bookings.changeAllowed(q.tenantId(), b)) {
            return Answer.handover(HandoverReason.BOOKING, BookingWording.pastCutoff(q.language()));
        }
        try {
            return confirmed(q, bookings.move(q.tenantId(), b.id(), resourceId, start, "customer"));
        } catch (BookingSlotTakenException e) {
            BookableService service = bookings.service(q.tenantId(), b.serviceId())
                    .orElseThrow(() -> new NotFoundException("Service not found"));
            return offerTimes(q, service, b, BookingWording.slotTaken(q.language()));
        } catch (BookingRuleException e) {
            return Answer.reply(BookingWording.noUpcoming(q.language()), null, List.of());
        }
    }

    private Answer askCancel(Question q, Booking b) {
        if (!bookings.changeAllowed(q.tenantId(), b)) {
            return Answer.handover(HandoverReason.BOOKING, BookingWording.pastCutoff(q.language()));
        }
        String lang = q.language();
        return Answer.choices(BookingWording.confirmCancel(b, b.startsAt().atZone(zone(q.tenantId())), lang),
                Interactive.buttons(List.of(
                        new Interactive.Choice(PREFIX + "c:" + b.id(), BookingWording.cancelLabel(lang), null),
                        new Interactive.Choice(PREFIX + "k:" + b.id(), BookingWording.keepLabel(lang), null))));
    }

    private Answer cancel(Question q, Booking b) {
        if (!bookings.changeAllowed(q.tenantId(), b)) {
            return Answer.handover(HandoverReason.BOOKING, BookingWording.pastCutoff(q.language()));
        }
        bookings.cancel(q.tenantId(), b.id(), "customer");
        return Answer.reply(BookingWording.cancelledByCustomer(q.language()), null, List.of());
    }

    private Answer confirmed(Question q, Booking b) {
        String lang = q.language();
        return Answer.choices(BookingWording.confirmed(b, b.startsAt().atZone(zone(q.tenantId())), lang),
                Interactive.buttons(List.of(
                        new Interactive.Choice(PREFIX + "r:" + b.id(), BookingWording.rescheduleLabel(lang), null),
                        new Interactive.Choice(PREFIX + "x:" + b.id(), BookingWording.cancelLabel(lang), null))));
    }

    /**
     * "1", "2", "3" or "Confirm", "Reschedule", "Cancel" after a reminder or confirmation that wasn't sent with
     * tappable options (a template outside the 24-hour window), mapped to the matching option.
     */
    private String awaitingReplyId(Question q) {
        String text = q.text() == null ? "" : q.text().strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.length() > 20) {
            return null;
        }
        Optional<Booking> awaiting = bookings.awaitingReply(q.tenantId(), q.customerId());
        if (awaiting.isEmpty()) {
            return null;
        }
        Booking b = awaiting.get();
        boolean reminder = "reminder".equals(b.awaitingReply());
        if (reminder && (text.equals("1") || CONFIRM_WORDS.contains(text))) {
            return PREFIX + "y:" + b.id();
        }
        if (text.equals(reminder ? "2" : "1") || RESCHEDULE_WORDS.contains(text)) {
            return PREFIX + "r:" + b.id();
        }
        if (text.equals(reminder ? "3" : "2") || CANCEL_WORDS.contains(text)) {
            return PREFIX + "x:" + b.id();
        }
        return null;
    }

    private BookableService service(TenantId t, String id) {
        return bookings.service(t, UUID.fromString(id)).orElseThrow(() -> new NotFoundException("Service not found"));
    }

    /** The booking must belong to this tenant (the query) and this customer (checked here). */
    private Booking own(Question q, String id) {
        Booking b = bookings.find(q.tenantId(), UUID.fromString(id))
                .filter(x -> x.customerId().equals(q.customerId()))
                .filter(x -> x.status().open())
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        return b;
    }

    private static UUID resource(String part) {
        return "0".equals(part) ? null : UUID.fromString(part);
    }

    private ZoneId zone(TenantId t) {
        return ZoneId.of(tenants.get(t).timezone());
    }
}
