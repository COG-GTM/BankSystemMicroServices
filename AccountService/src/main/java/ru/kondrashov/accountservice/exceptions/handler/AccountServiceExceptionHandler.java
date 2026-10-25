package ru.kondrashov.accountservice.exceptions.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;

import java.util.stream.Collectors;

@ControllerAdvice
public class AccountServiceExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LogManager.getLogger(AccountServiceExceptionHandler.class);

    @ExceptionHandler({AccountNotFoundException.class, BillNotFoundException.class})
    public ResponseEntity<AccountServiceExceptionTemplate> notFoundExceptions(RuntimeException exp) {
        return build(exp, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NotEnoughMoneyException.class)
    public ResponseEntity<AccountServiceExceptionTemplate> notEnoughMoneyExceptions(NotEnoughMoneyException exp) {
        return build(exp, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<AccountServiceExceptionTemplate> responseStatusExceptions(ResponseStatusException exp) {
        String message = exp.getReason() != null ? exp.getReason() : exp.getStatus().getReasonPhrase();
        return build(exp, message, exp.getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AccountServiceExceptionTemplate> unexpectedExceptions(Exception exp) {
        LOGGER.error("Unhandled exception while processing request", exp);
        return build(exp, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatus status,
                                                                  WebRequest request) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .sorted()
                .collect(Collectors.joining("; "));
        return super.handleExceptionInternal(ex, template(ex, message, status), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatus status,
                                                             WebRequest request) {
        Object template = body instanceof AccountServiceExceptionTemplate
                ? body
                : template(ex, status.getReasonPhrase(), status);
        return super.handleExceptionInternal(ex, template, headers, status, request);
    }

    private static ResponseEntity<AccountServiceExceptionTemplate> build(Exception exp, HttpStatus status) {
        return build(exp, exp.getMessage(), status);
    }

    private static ResponseEntity<AccountServiceExceptionTemplate> build(Exception exp, String message, HttpStatus status) {
        return new ResponseEntity<>(template(exp, message, status), status);
    }

    private static AccountServiceExceptionTemplate template(Exception exp, String message, HttpStatus status) {
        return new AccountServiceExceptionTemplate(message, status.value(), exp.getClass().getSimpleName());
    }
}
