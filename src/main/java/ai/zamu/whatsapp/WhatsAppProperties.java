package ai.zamu.whatsapp;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WhatsApp Cloud API settings (spec: External integrations, WhatsApp). One Meta app for all tenants: the app
 * secret signs every webhook (MSG-01) and the verify token answers Meta's subscription handshake. The Graph API
 * version is pinned (spec: "use a pinned Graph API version and plan upgrades"); confirm it against Meta's current
 * documentation before go-live.
 */
@ConfigurationProperties("zamu.whatsapp")
record WhatsAppProperties(String appSecret, String verifyToken, String graphBaseUrl, String graphVersion,
        Duration connectTimeout, Duration readTimeout) {
}
