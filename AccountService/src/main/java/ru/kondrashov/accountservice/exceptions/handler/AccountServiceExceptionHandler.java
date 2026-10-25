package ru.kondrashov.accountservice.exceptions.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;

import java.util.stream.Collectors;

@ControllerAdvice
public class AccountServiceExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<AccountServiceExceptionTemplate> notFoundExceptions(AccountNotFoundException exp) {
        return new ResponseEntity<>(
                new AccountServiceExceptionTemplate(
                        exp.getMessage(),
                        404,
                        "AccountNotFoundException"),
                HttpStatus.NOT_FOUND);

    }

    @ExceptionHandler(BillNotFoundException.class)
    public ResponseEntity<AccountServiceExceptionTemplate> notFoundExceptions(BillNotFoundException exp) {
        return new ResponseEntity<>(
                new AccountServiceExceptionTemplate(
                        exp.getMessage(),
                        404,
                        "BillNotFoundException"),
                HttpStatus.NOT_FOUND);

    }

    @ExceptionHandler(NotEnoughMoneyException.class)
    public ResponseEntity<AccountServiceExceptionTemplate> notEnoughMoneyExceptions(NotEnoughMoneyException exp) {
        return new ResponseEntity<>(
                new AccountServiceExceptionTemplate(
                        exp.getMessage(),
                        400,
                        "NotEnoughMoneyException"),
                HttpStatus.BAD_REQUEST);

    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AccountServiceExceptionTemplate> validationExceptions(MethodArgumentNotValidException exp) {
        String message = exp.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return new ResponseEntity<>(
                new AccountServiceExceptionTemplate(
                        message,
                        400,
                        "MethodArgumentNotValidException"),
                HttpStatus.BAD_REQUEST);
    }
}
