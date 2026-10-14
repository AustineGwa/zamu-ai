package ai.zamu.whatsapp;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureTest {

    private static final String SECRET = "app-secret";
    private static final byte[] BODY = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void MSG_01_acceptsTheSignatureMetaComputes() {
        assertThat(WebhookSignature.valid(SECRET, BODY, WebhookSignature.header(SECRET, BODY))).isTrue();
    }

    @Test
    void MSG_01_acceptsUpperCaseHex() {
        String header = "sha256=" + WebhookSignature.header(SECRET, BODY).substring(7).toUpperCase();
        assertThat(WebhookSignature.valid(SECRET, BODY, header)).isTrue();
    }

    @Test
    void MSG_01_rejectsMissingWrongOrMalformedSignatures() {
        String good = WebhookSignature.header(SECRET, BODY);
        assertThat(WebhookSignature.valid(SECRET, BODY, null)).isFalse();
        assertThat(WebhookSignature.valid(SECRET, BODY, "")).isFalse();
        assertThat(WebhookSignature.valid(SECRET, BODY, good.substring(7))).as("no sha256= prefix").isFalse();
        assertThat(WebhookSignature.valid(SECRET, BODY, "sha256=zz")).isFalse();
        assertThat(WebhookSignature.valid("other-secret", BODY, good)).isFalse();
        assertThat(WebhookSignature.valid(SECRET, "{}".getBytes(StandardCharsets.UTF_8), good)).as("tampered body").isFalse();
    }

    @Test
    void MSG_01_rejectsEverythingWhenNoAppSecretIsConfigured() {
        assertThat(WebhookSignature.valid("", BODY, WebhookSignature.header("x", BODY))).isFalse();
        assertThat(WebhookSignature.valid(null, BODY, WebhookSignature.header("x", BODY))).isFalse();
    }
}
