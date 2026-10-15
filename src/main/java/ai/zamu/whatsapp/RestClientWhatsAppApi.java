package ai.zamu.whatsapp;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import ai.zamu.conversations.Interactive;

/**
 * WhatsApp Cloud API over HTTPS (spec: External integrations, WhatsApp). One client for all tenants; each call
 * carries the tenant's own token.
 *
 * <p><b>Check before go-live:</b> request shapes follow Meta's Cloud API documentation as of this writing
 * (Graph API version from {@code zamu.whatsapp.graph-version}). Meta changes these; confirm the messages,
 * mark-as-read with typing indicator, and message_templates endpoints against the current docs.
 */
@Component
@Profile("!h2")
class RestClientWhatsAppApi implements WhatsAppApi {

    private final RestClient client;
    private final ObjectMapper objectMapper;

    RestClientWhatsAppApi(WhatsAppProperties properties, ObjectMapper objectMapper) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(properties.graphBaseUrl() + "/" + properties.graphVersion())
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String sendText(Credentials credentials, String to, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", to);
        body.put("type", "text");
        body.put("text", Map.of("preview_url", false, "body", text));
        return messageId(post(credentials, "/" + credentials.phoneNumberId() + "/messages", body));
    }

    @Override
    public String sendTemplate(Credentials credentials, String to, String name, String language, List<String> params) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", name);
        template.put("language", Map.of("code", language));
        if (!params.isEmpty()) {
            List<Map<String, Object>> parameters = new ArrayList<>();
            for (String p : params) {
                parameters.add(Map.of("type", "text", "text", p));
            }
            template.put("components", List.of(Map.of("type", "body", "parameters", parameters)));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", to);
        body.put("type", "template");
        body.put("template", template);
        return messageId(post(credentials, "/" + credentials.phoneNumberId() + "/messages", body));
    }

    /**
     * Interactive list or reply-button message. ASSUMPTION: payload shape as in Meta's Cloud API reference
     * ("interactive" with type "list" and sections/rows, or type "button" with action.buttons of type "reply").
     */
    @Override
    public String sendInteractive(Credentials credentials, String to, String text, Interactive options) {
        Map<String, Object> interactive = new LinkedHashMap<>();
        interactive.put("body", Map.of("text", text));
        if (options.asButtons()) {
            interactive.put("type", "button");
            List<Map<String, Object>> buttons = new ArrayList<>();
            for (Interactive.Choice c : options.choices()) {
                buttons.add(Map.of("type", "reply", "reply", Map.of("id", c.id(), "title", c.title())));
            }
            interactive.put("action", Map.of("buttons", buttons));
        } else {
            interactive.put("type", "list");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Interactive.Choice c : options.choices()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", c.id());
                row.put("title", c.title());
                if (c.description() != null && !c.description().isBlank()) {
                    row.put("description", c.description());
                }
                rows.add(row);
            }
            interactive.put("action", Map.of("button", options.button(), "sections", List.of(Map.of("rows", rows))));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", to);
        body.put("type", "interactive");
        body.put("interactive", interactive);
        return messageId(post(credentials, "/" + credentials.phoneNumberId() + "/messages", body));
    }

    @Override
    public void markReadWithTyping(Credentials credentials, String waMessageId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("status", "read");
        body.put("message_id", waMessageId);
        body.put("typing_indicator", Map.of("type", "text"));
        post(credentials, "/" + credentials.phoneNumberId() + "/messages", body);
    }

    @Override
    public String submitTemplate(Credentials credentials, String name, String category, String language, String body,
            int parameterCount) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("type", "BODY");
        component.put("text", body);
        if (parameterCount > 0) {
            // Meta requires sample values for each placeholder.
            List<String> samples = new ArrayList<>();
            for (int i = 1; i <= parameterCount; i++) {
                samples.add("example " + i);
            }
            component.put("example", Map.of("body_text", List.of(samples)));
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("category", category.toUpperCase(java.util.Locale.ROOT));
        request.put("language", language);
        request.put("components", List.of(component));
        JsonNode response = post(credentials, "/" + credentials.wabaId() + "/message_templates", request);
        return response.path("id").asString("");
    }

    private JsonNode post(Credentials credentials, String path, Map<String, Object> body) {
        try {
            String response = client.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + credentials.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response == null ? "{}" : response);
        } catch (RestClientResponseException e) {
            throw new WhatsAppApiException(errorCode(e), e);
        } catch (ResourceAccessException e) {
            throw new WhatsAppApiException("network", e);
        }
    }

    /** Meta's numeric error code, e.g. 131047 (re-engagement message outside the window), else the HTTP status. */
    private String errorCode(RestClientResponseException e) {
        try {
            JsonNode code = objectMapper.readTree(e.getResponseBodyAsString()).path("error").path("code");
            if (!code.isMissingNode() && !code.isNull()) {
                return code.asString("http_" + e.getStatusCode().value());
            }
        } catch (RuntimeException ignored) {
            // not JSON
        }
        return "http_" + e.getStatusCode().value();
    }

    private static String messageId(JsonNode response) {
        JsonNode id = response.path("messages").path(0).path("id");
        String value = id.asString("");
        if (value.isBlank()) {
            throw new WhatsAppApiException("no_message_id", null);
        }
        return value;
    }
}
