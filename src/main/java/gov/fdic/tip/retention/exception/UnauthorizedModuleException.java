package gov.fdic.tip.retention.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedModuleException extends RuntimeException {
    public UnauthorizedModuleException(String msg) { super(msg); }
}
