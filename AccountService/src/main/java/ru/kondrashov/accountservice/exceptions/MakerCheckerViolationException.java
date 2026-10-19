package ru.kondrashov.accountservice.exceptions;

public class MakerCheckerViolationException extends AuditRejectedException {

    public MakerCheckerViolationException(String message) {
        super(message);
    }
}
