package ai.zamu.bookings;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A service customers can book (spec: services), with the resources that can deliver it. */
public record BookableService(UUID id, String name, String nameSw, String description, int durationMin,
        BigDecimal priceMin, BigDecimal priceMax, BigDecimal depositAmount, int bufferMin, boolean active,
        List<UUID> resourceIds) {

    public BookableService {
        resourceIds = resourceIds == null ? List.of() : List.copyOf(resourceIds);
    }

    public String name(String language) {
        return "sw".equals(language) && nameSw != null && !nameSw.isBlank() ? nameSw : name;
    }

    public boolean needsDeposit() {
        return depositAmount != null && depositAmount.signum() > 0;
    }
}
