package edu.wpi.citizens.openbanking;

import edu.wpi.citizens.openbanking.fdx.FdxError;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns every failure into an FDX Error body. */
@RestControllerAdvice
public class FdxExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FdxExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public FdxError accountNotFound(AccountNotFoundException e) {
        return FdxError.ACCOUNT_NOT_FOUND;
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public FdxError notAuthorized(AccessDeniedException e) {
        return FdxError.NOT_AUTHORIZED;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public FdxError unexpected(Exception e) {
        log.error("unexpected error", e);
        return FdxError.INTERNAL_ERROR;
    }
}
