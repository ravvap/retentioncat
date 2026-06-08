package gov.fdic.tip.retention.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
    public static ResourceNotFoundException category(Object id) {
        return new ResourceNotFoundException("Category not found: " + id);
    }
    public static ResourceNotFoundException subCategory(Object id) {
        return new ResourceNotFoundException("Sub-Category not found: " + id);
    }
}
