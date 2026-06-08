package gov.fdic.tip.retention.exception;

public class UnprocessableEntityException extends RuntimeException {
    public UnprocessableEntityException(String message) { super(message); }
}
