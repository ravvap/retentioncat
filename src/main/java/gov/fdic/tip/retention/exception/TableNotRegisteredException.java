package gov.fdic.tip.retention.exception;
public class TableNotRegisteredException extends RuntimeException {
    public TableNotRegisteredException(String detail) {
        super("Table is not registered for Pattern B retention: " + detail);
    }
}
