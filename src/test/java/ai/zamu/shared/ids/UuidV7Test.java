package ai.zamu.shared.ids;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    @Test
    void hasVersion7AndRfcVariant() {
        UUID id = UuidV7.next();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheTimestamp() {
        long before = System.currentTimeMillis();
        UUID id = UuidV7.next();
        long embedded = id.getMostSignificantBits() >>> 16;
        // The counter can run a few milliseconds ahead after a burst of IDs in one millisecond.
        assertThat(embedded).isBetween(before, System.currentTimeMillis() + 100);
    }

    @Test
    void MSG_05_idsAreStrictlyIncreasingEvenWithinOneMillisecond() {
        long frozen = System.currentTimeMillis();
        List<byte[]> ids = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(Uuids.toBytes(UuidV7.next(frozen)));
        }
        for (int i = 1; i < ids.size(); i++) {
            assertThat(Arrays.compareUnsigned(ids.get(i - 1), ids.get(i))).isNegative();
        }
    }

    @Test
    void binaryRoundTrip() {
        UUID id = UuidV7.next();
        assertThat(Uuids.fromBytes(Uuids.toBytes(id))).isEqualTo(id);
        assertThat(Uuids.toBytes(null)).isNull();
        assertThat(Uuids.parseOrNull("not-a-uuid")).isNull();
    }
}
