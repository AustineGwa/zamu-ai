package ai.zamu.reports;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.conversations.ConversationEvents;
import ai.zamu.conversations.MessageDirection;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.time.ZamuTime;

/**
 * Usage metering for billing (MSG-17, REP-03). Listens to conversation events in the same transaction, so counts
 * match what was stored. Days are Nairobi calendar days (NFR-18).
 */
@Service
public class UsageService {

    private final UsageRepository repository;

    UsageService(UsageRepository repository) {
        this.repository = repository;
    }

    @EventListener
    public void on(ConversationEvents.BillableConversationStarted event) {
        repository.increment(event.tenantId(), day(event.at()), "conversations");
    }

    @EventListener
    public void on(ConversationEvents.MessageRecorded event) {
        repository.increment(event.tenantId(), day(event.at()),
                event.direction() == MessageDirection.IN ? "messages_in" : "messages_out");
    }

    @Transactional(readOnly = true)
    public Optional<DailyUsage> day(TenantId tenantId, LocalDate date) {
        return repository.find(tenantId, date);
    }

    private static LocalDate day(Instant at) {
        return at.atZone(ZamuTime.NAIROBI).toLocalDate();
    }
}
