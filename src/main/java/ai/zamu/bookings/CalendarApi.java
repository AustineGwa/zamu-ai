package ai.zamu.bookings;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The calendar provider's API (Google Calendar in version 1, spec: INT Google Calendar). Implemented over HTTP by
 * {@link GoogleCalendarApi}; tests replace it.
 */
interface CalendarApi {

    /** A busy period on a calendar. */
    record Busy(Instant start, Instant end) {
    }

    /** What Zamu writes on an event. {@code tentative} while held or awaiting the deposit (BK-05). */
    record EventDetails(String summary, String description, Instant start, Instant end, boolean tentative) {
    }

    /** An event as the calendar has it now (BK-10). */
    record EventState(boolean cancelled, Instant start, Instant end) {
    }

    record Tokens(String accessToken, long expiresInSeconds, String refreshToken) {
    }

    String authorizationUrl(String redirectUri, String state);

    Tokens exchangeCode(String code, String redirectUri);

    Tokens refresh(String refreshToken);

    /** Busy periods per calendar ID, between {@code from} and {@code to}. */
    Map<String, List<Busy>> freeBusy(String accessToken, List<String> calendarIds, Instant from, Instant to);

    String insertEvent(String accessToken, String calendarId, EventDetails event);

    void patchEvent(String accessToken, String calendarId, String eventId, EventDetails event);

    void deleteEvent(String accessToken, String calendarId, String eventId);

    /** Empty when the event no longer exists. */
    Optional<EventState> getEvent(String accessToken, String calendarId, String eventId);
}
