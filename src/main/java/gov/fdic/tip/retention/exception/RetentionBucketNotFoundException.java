package gov.fdic.tip.retention.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a required retention bucket does not exist or is inactive (AC-3). */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class RetentionBucketNotFoundException extends RuntimeException {
    public RetentionBucketNotFoundException(String bucketCode) {
        super("Retention bucket not found or inactive: " + bucketCode);
    }
}
