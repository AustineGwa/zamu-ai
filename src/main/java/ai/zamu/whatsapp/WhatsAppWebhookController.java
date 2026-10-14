package ai.zamu.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Meta webhook (spec: Web routes, GET/POST /webhooks/whatsapp). Public, without CSRF (SecurityConfig); the
 * signature is the authentication (MSG-01).
 */
@RestController
@RequestMapping("/webhooks/whatsapp")
class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppProperties properties;
    private final WhatsAppWebhookService service;

    WhatsAppWebhookController(WhatsAppProperties properties, WhatsAppWebhookService service) {
        this.properties = properties;
        this.service = service;
    }

    /** Meta's subscription handshake: echo the challenge when the verify token matches. */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        String expected = properties.verifyToken();
        if ("subscribe".equals(mode) && expected != null && !expected.isBlank()
                && java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        String.valueOf(token).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                && challenge != null) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).build();
    }

    /** MSG-01: 401 without a valid signature; otherwise stored, queued and answered 200 straight away. */
    @PostMapping
    ResponseEntity<Void> receive(@RequestBody(required = false) byte[] body,
            @RequestHeader(name = WebhookSignature.HEADER, required = false) String signature) {
        if (!WebhookSignature.valid(properties.appSecret(), body, signature)) {
            log.warn("WhatsApp webhook rejected: bad or missing signature");
            return ResponseEntity.status(401).build();
        }
        service.receive(body);
        return ResponseEntity.ok().build();
    }
}
