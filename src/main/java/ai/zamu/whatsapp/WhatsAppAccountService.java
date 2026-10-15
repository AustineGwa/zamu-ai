package ai.zamu.whatsapp;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.crypto.SecretCipher;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;

/** Connecting a tenant's WhatsApp number (spec: Tenant setup wizard, WhatsApp step; NFR-06 for the token). */
@Service
public class WhatsAppAccountService {

    private final WhatsAppAccountRepository repository;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final Clock clock;

    WhatsAppAccountService(WhatsAppAccountRepository repository, SecretCipher cipher, AuditService audit, Clock clock) {
        this.repository = repository;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<WhatsAppAccountView> view(TenantId tenantId) {
        return repository.findByTenant(tenantId).map(WhatsAppAccount::toView);
    }

    /** Creates or updates the connection. The token is required the first time and optional after. */
    @Transactional
    public void connect(TenantId tenantId, WhatsAppConnectForm form) {
        String phoneNumberId = form.phoneNumberId().trim();
        if (repository.phoneNumberIdTakenByOther(phoneNumberId, tenantId.value())) {
            throw new WhatsAppSetupException("This phone number ID is already connected to another business.");
        }
        String token = form.accessToken() == null ? "" : form.accessToken().trim();
        Instant now = clock.instant();
        Optional<WhatsAppAccount> existing = repository.findByTenant(tenantId);
        if (existing.isEmpty()) {
            if (token.isEmpty()) {
                throw new WhatsAppSetupException("Enter the access token.");
            }
            repository.insert(tenantId, UuidV7.next(clock), form.wabaId().trim(), phoneNumberId,
                    form.displayPhone().trim(), cipher.encryptString(token), now);
        } else {
            repository.update(tenantId, form.wabaId().trim(), phoneNumberId, form.displayPhone().trim(),
                    token.isEmpty() ? null : cipher.encryptString(token), now);
        }
        // The token never appears in the audit log; only whether it changed.
        audit.record(AuditEvent.of(tenantId, "whatsapp.connected", "tenant", tenantId.value())
                .withAfter(Map.of("phoneNumberId", phoneNumberId, "tokenChanged", !token.isEmpty())));
    }

    Optional<TenantId> tenantFor(String phoneNumberId) {
        return repository.findByPhoneNumberId(phoneNumberId).map(a -> TenantId.of(a.tenantId()));
    }

    Optional<TenantId> tenantForWaba(String wabaId) {
        return repository.findByWabaId(wabaId).map(a -> TenantId.of(a.tenantId()));
    }

    Optional<Credentials> credentials(TenantId tenantId) {
        return repository.findByTenant(tenantId)
                .map(a -> new Credentials(a.phoneNumberId(), a.wabaId(), cipher.decryptString(a.encryptedAccessToken())));
    }

    void markWebhookVerified(TenantId tenantId) {
        repository.markWebhookVerified(tenantId);
    }

    /** NFR-11: offboarding purge; the access token goes with the row. */
    @Transactional
    public void purgeTenant(TenantId tenantId) {
        repository.deleteForTenant(tenantId);
    }
}
