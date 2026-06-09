package gov.fdic.tip.retention.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    record ErrorResponse(int status, String error, String message, OffsetDateTime timestamp, Object details) {}

    @ExceptionHandler(RetentionBucketNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBucketNotFound(RetentionBucketNotFoundException ex) {
        log.warn("Retention bucket not found: {}", ex.getMessage());
        return new ErrorResponse(400, "BAD_REQUEST", ex.getMessage(), OffsetDateTime.now(), null);
    }

    @ExceptionHandler(UnauthorizedModuleException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleUnauthorized(UnauthorizedModuleException ex) {
        log.warn("Unauthorised module: {}", ex.getMessage());
        return new ErrorResponse(401, "UNAUTHORIZED", ex.getMessage(), OffsetDateTime.now(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return new ErrorResponse(400, "VALIDATION_FAILED", "Request validation failed",
                OffsetDateTime.now(), fieldErrors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIllegalArg(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return new ErrorResponse(409, "CONFLICT", ex.getMessage(), OffsetDateTime.now(), null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleAll(Exception ex) {
        log.error("Unhandled exception", ex);
        return new ErrorResponse(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred",
                OffsetDateTime.now(), null);
    }
}
