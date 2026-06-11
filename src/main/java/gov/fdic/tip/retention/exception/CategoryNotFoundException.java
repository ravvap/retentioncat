package gov.fdic.tip.retention.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(String detail) { super("Category not found or inactive: " + detail); }
}
