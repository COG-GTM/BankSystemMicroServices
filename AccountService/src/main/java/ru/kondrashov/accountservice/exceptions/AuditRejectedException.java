package ru.kondrashov.accountservice.exceptions;

/** A request refused before any state change; audited with outcome REJECTED. */
public abstract class AuditRejectedException extends RuntimeException {

    protected AuditRejectedException(String message) {
        super(message);
    }
}
