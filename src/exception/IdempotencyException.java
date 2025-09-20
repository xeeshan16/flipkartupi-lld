package exception;

public class IdempotencyException extends Exception {
    public IdempotencyException(String msg) { super(msg); }
}
