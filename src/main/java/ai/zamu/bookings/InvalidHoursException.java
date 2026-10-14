package ai.zamu.bookings;

public class InvalidHoursException extends RuntimeException {

    public InvalidHoursException(String message) {
        super(message);
    }
}
