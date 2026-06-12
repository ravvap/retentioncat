package gov.fdic.tip.retention.exception;
public class DuplicateRegistrationException extends RuntimeException {
    public DuplicateRegistrationException(String detail) {
        super("Table is already registered for Pattern B retention: " + detail);
    }
}
