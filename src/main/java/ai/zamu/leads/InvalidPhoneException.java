package ai.zamu.leads;

public class InvalidPhoneException extends RuntimeException {

    public InvalidPhoneException() {
        super("Enter a Kenyan mobile number, for example 0712 345 678");
    }
}
