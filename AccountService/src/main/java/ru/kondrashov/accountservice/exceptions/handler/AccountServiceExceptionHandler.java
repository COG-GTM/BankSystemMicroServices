package ru.kondrashov.accountservice.exceptions.handler;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;

import java.util.stream.Collectors;

@ControllerAdvice
public class AccountServiceExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({AccountNotFoundException.class, BillNotFoundException.class})
    public ResponseEntity<AccountServiceExceptionTemplate> notFoundExceptions(RuntimeException exp) {
        return errorResponse(HttpStatus.NOT_FOUND, exp, exp.getMessage());
    }

    @ExceptionHandler(NotEnoughMoneyException.class)
    public ResponseEntity<AccountServiceExceptionTemplate> notEnoughMoneyExceptions(NotEnoughMoneyException exp) {
        return errorResponse(HttpStatus.BAD_REQUEST, exp, exp.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatus status, WebRequest request) {
        if (body == null) {
            body = template(status, ex, frameworkErrorMessage(ex, status));
        }
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ResponseEntity<AccountServiceExceptionTemplate> errorResponse(HttpStatus status, Exception ex, String message) {
        return new ResponseEntity<>(template(status, ex, message), status);
    }

    private static AccountServiceExceptionTemplate template(HttpStatus status, Exception ex, String message) {
        return new AccountServiceExceptionTemplate(message, status.value(), ex.getClass().getSimpleName());
    }

    private static String frameworkErrorMessage(Exception ex, HttpStatus status) {
        BindingResult bindingResult = null;
        if (ex instanceof MethodArgumentNotValidException) {
            bindingResult = ((MethodArgumentNotValidException) ex).getBindingResult();
        } else if (ex instanceof BindException) {
            bindingResult = ((BindException) ex).getBindingResult();
        }
        if (bindingResult == null || !bindingResult.hasErrors()) {
            return status.getReasonPhrase();
        }
        return bindingResult.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
    }
}
