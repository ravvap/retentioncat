package gov.fdic.tip.retention.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    record ErrorBody(int status, String error, String message,
                     OffsetDateTime timestamp, Object details) {}

    @ExceptionHandler(SubCategoryNotFoundException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorBody handleSubCat(SubCategoryNotFoundException ex) {
        log.warn("422 SubCategoryNotFound: {}", ex.getMessage());
        return body(422, "UNPROCESSABLE_ENTITY", ex.getMessage(), null);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorBody handleCategory(CategoryNotFoundException ex) {
        log.warn("404 CategoryNotFound: {}", ex.getMessage());
        return body(404, "NOT_FOUND", ex.getMessage(), null);
    }

    @ExceptionHandler(TableNotRegisteredException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorBody handleTableNotReg(TableNotRegisteredException ex) {
        log.warn("422 TableNotRegistered: {}", ex.getMessage());
        return body(422, "UNPROCESSABLE_ENTITY", ex.getMessage(), null);
    }

    @ExceptionHandler(DuplicateRegistrationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorBody handleDuplicate(DuplicateRegistrationException ex) {
        log.warn("409 DuplicateRegistration: {}", ex.getMessage());
        return body(409, "CONFLICT", ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorBody handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("400 ValidationFailed: {}", errors);
        return body(400, "VALIDATION_FAILED", "Request validation failed", errors);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorBody handleAll(Exception ex) {
        log.error("500 Unhandled exception", ex);
        return body(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred", null);
    }

    private ErrorBody body(int status, String error, String message, Object details) {
        return new ErrorBody(status, error, message, OffsetDateTime.now(), details);
    }
}
