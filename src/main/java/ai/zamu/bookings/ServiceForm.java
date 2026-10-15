package ai.zamu.bookings;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A bookable service, as entered in the setup wizard (spec: services). */
public record ServiceForm(
        @NotBlank(message = "Enter the service name") @Size(max = 120) String name,
        @Size(max = 120) String nameSw,
        @Size(max = 500) String description,
        @NotNull(message = "Enter how long it takes") @Min(value = 5, message = "At least 5 minutes")
        @Max(value = 600, message = "At most 10 hours") Integer durationMin,
        @DecimalMin(value = "0", message = "Can't be negative") BigDecimal priceMin,
        @DecimalMin(value = "0", message = "Can't be negative") BigDecimal priceMax,
        @DecimalMin(value = "0", message = "Can't be negative") BigDecimal depositAmount,
        @Min(value = 0, message = "Can't be negative") @Max(value = 240) Integer bufferMin,
        boolean active,
        List<UUID> resourceIds) {

    public ServiceForm {
        resourceIds = resourceIds == null ? List.of() : List.copyOf(resourceIds);
    }

    static ServiceForm empty() {
        return new ServiceForm("", "", "", 30, null, null, BigDecimal.ZERO, 0, true, List.of());
    }

    static ServiceForm from(BookableService s) {
        return new ServiceForm(s.name(), s.nameSw(), s.description(), s.durationMin(), s.priceMin(), s.priceMax(),
                s.depositAmount(), s.bufferMin(), s.active(), s.resourceIds());
    }
}
