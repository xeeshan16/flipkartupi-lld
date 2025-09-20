package exception;


/** Thrown when a payment cannot proceed because the bank is down/unavailable. */
public class BankDownException extends RuntimeException {
    public BankDownException(String message) { super(message); }
    public BankDownException(String message, Throwable cause) { super(message, cause); }
}
