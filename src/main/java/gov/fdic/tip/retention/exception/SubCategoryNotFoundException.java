package gov.fdic.tip.retention.exception;

public class SubCategoryNotFoundException extends RuntimeException {
    public SubCategoryNotFoundException(String code) {
        super("Sub-Category not found, inactive, or classification not allowed: " + code);
    }
}
