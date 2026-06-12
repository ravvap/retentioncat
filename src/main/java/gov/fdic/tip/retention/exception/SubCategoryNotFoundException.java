package gov.fdic.tip.retention.exception;
public class SubCategoryNotFoundException extends RuntimeException {
    public SubCategoryNotFoundException(String detail) {
        super("Sub-Category not found, inactive, or classification not allowed: " + detail);
    }
}
