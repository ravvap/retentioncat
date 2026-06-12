package gov.fdic.tip.retention.exception;
public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(String detail) {
        super("Retention category not found or inactive: " + detail);
    }
}
