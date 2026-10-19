package ru.kondrashov.accountservice.exceptions;

public class MissingActorException extends AuditRejectedException {

    public MissingActorException(String message) {
        super(message);
    }
}
