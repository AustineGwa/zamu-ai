package ai.zamu.bookings;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Google Calendar API v3 and Google OAuth 2.0 over HTTP. ASSUMPTION: endpoints, scopes and payload shapes as in
 * Google's public documentation at the time of writing (OAuth: accounts.google.com/o/oauth2/v2/auth and
 * oauth2.googleapis.com/token; Calendar: www.googleapis.com/calendar/v3 freeBusy and events). Confirm before
 * go-live; Google changes scopes and verification rules from time to time.
 */
@Component
class GoogleCalendarApi implements CalendarApi {

    static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String API = "https://www.googleapis.com/calendar/v3";
    /** Least privilege: events (create, update, delete Zamu's bookings) and free/busy only (spec: INT). */
    static final String SCOPES = "https://www.googleapis.com/auth/calendar.events "
            + "https://www.googleapis.com/auth/calendar.freebusy";

    private final GoogleProperties properties;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    GoogleCalendarApi(GoogleProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.timeout());
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String authorizationUrl(String redirectUri, String state) {
        return AUTH_URL + "?response_type=code&access_type=offline&prompt=consent&include_granted_scopes=true"
                + "&client_id=" + enc(properties.clientId()) + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc(SCOPES) + "&state=" + enc(state);
    }

    @Override
    public Tokens exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        return token(form);
    }

    @Override
    public Tokens refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return token(form);
    }

    private Tokens token(MultiValueMap<String, String> form) {
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        JsonNode json = call(() -> client.post().uri(TOKEN_URL).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(String.class));
        String access = json.path("access_token").asString("");
        if (access.isEmpty()) {
            throw new CalendarApiException("No access token in Google's reply", false);
        }
        String refresh = json.path("refresh_token").asString("");
        return new Tokens(access, json.path("expires_in").asLong(3600), refresh.isEmpty() ? null : refresh);
    }

    @Override
    public Map<String, List<Busy>> freeBusy(String accessToken, List<String> calendarIds, Instant from, Instant to) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timeMin", from.toString());
        body.put("timeMax", to.toString());
        body.put("items", calendarIds.stream().map(id -> Map.of("id", id)).toList());
        JsonNode json = call(() -> client.post().uri(API + "/freeBusy").header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));
        Map<String, List<Busy>> result = new LinkedHashMap<>();
        for (String id : calendarIds) {
            JsonNode calendar = json.path("calendars").path(id);
            if (calendar.path("errors").size() > 0) {
                throw new CalendarApiException("Free/busy error for a calendar: "
                        + calendar.path("errors").path(0).path("reason").asString("unknown"), false);
            }
            List<Busy> busy = new ArrayList<>();
            for (JsonNode b : calendar.path("busy")) {
                busy.add(new Busy(Instant.parse(b.path("start").asString()), Instant.parse(b.path("end").asString())));
            }
            result.put(id, busy);
        }
        return result;
    }

    @Override
    public String insertEvent(String accessToken, String calendarId, EventDetails event) {
        JsonNode json = call(() -> client.post().uri(API + "/calendars/{calendar}/events", calendarId)
                .header("Authorization", "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON)
                .body(eventBody(event)).retrieve().body(String.class));
        return json.path("id").asString("");
    }

    @Override
    public void patchEvent(String accessToken, String calendarId, String eventId, EventDetails event) {
        call(() -> client.patch().uri(API + "/calendars/{calendar}/events/{event}", calendarId, eventId)
                .header("Authorization", "Bearer " + accessToken).contentType(MediaType.APPLICATION_JSON)
                .body(eventBody(event)).retrieve().body(String.class));
    }

    @Override
    public void deleteEvent(String accessToken, String calendarId, String eventId) {
        try {
            call(() -> {
                client.delete().uri(API + "/calendars/{calendar}/events/{event}", calendarId, eventId)
                        .header("Authorization", "Bearer " + accessToken).retrieve().toBodilessEntity();
                return "{}";
            });
        } catch (NotFound e) {
            // already gone
        }
    }

    @Override
    public Optional<EventState> getEvent(String accessToken, String calendarId, String eventId) {
        try {
            JsonNode json = call(() -> client.get().uri(API + "/calendars/{calendar}/events/{event}", calendarId, eventId)
                    .header("Authorization", "Bearer " + accessToken).retrieve().body(String.class));
            String start = json.path("start").path("dateTime").asString("");
            String end = json.path("end").path("dateTime").asString("");
            return Optional.of(new EventState("cancelled".equals(json.path("status").asString("")),
                    start.isEmpty() ? null : java.time.OffsetDateTime.parse(start).toInstant(),
                    end.isEmpty() ? null : java.time.OffsetDateTime.parse(end).toInstant()));
        } catch (NotFound e) {
            return Optional.empty();
        }
    }

    private static Map<String, Object> eventBody(EventDetails e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", e.summary());
        body.put("description", e.description());
        body.put("start", Map.of("dateTime", e.start().toString()));
        body.put("end", Map.of("dateTime", e.end().toString()));
        body.put("status", e.tentative() ? "tentative" : "confirmed");
        return body;
    }

    private static final class NotFound extends RuntimeException {
    }

    private interface HttpCall {
        String run();
    }

    private JsonNode call(HttpCall call) {
        try {
            String body = call.run();
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 410) {
                throw new NotFound();
            }
            String error = e.getResponseBodyAsString();
            boolean revoked = status == 401 || error.contains("invalid_grant");
            throw new CalendarApiException("Google returned HTTP " + status, revoked);
        } catch (ResourceAccessException e) {
            throw new CalendarApiException("Google Calendar unreachable", false);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
