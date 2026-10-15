package ai.zamu.conversations;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.bookings.CustomerDirectory;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;

/** Customers for staff bookings (BK-09): the same customer record WhatsApp uses, found by phone number. */
@Component
class CustomerDirectoryAdapter implements CustomerDirectory {

    private final CustomerRepository customers;
    private final Clock clock;

    CustomerDirectoryAdapter(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID findOrCreate(TenantId tenantId, String phone, String name) {
        return customers.findByPhone(tenantId, phone).map(c -> {
            if (name != null && !name.isBlank() && c.name() == null) {
                customers.updateNameIfMissing(tenantId, c.id(), name.strip(), clock.instant());
            }
            return c.id();
        }).orElseGet(() -> {
            UUID id = UuidV7.next(clock);
            customers.insert(tenantId, id, phone, name == null || name.isBlank() ? null : name.strip(), clock.instant());
            return id;
        });
    }
}
