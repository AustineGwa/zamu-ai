package ai.zamu.shared.ids;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Converts UUIDs to and from MySQL BINARY(16). */
public final class Uuids {

    private Uuids() {
    }

    public static byte[] toBytes(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID must be 16 bytes, was " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /** Reads a nullable BINARY(16) column as a UUID. */
    public static UUID read(ResultSet rs, String column) throws SQLException {
        return fromBytes(rs.getBytes(column));
    }

    /** Parses a UUID from a path variable, returning null rather than throwing for malformed input. */
    public static UUID parseOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
