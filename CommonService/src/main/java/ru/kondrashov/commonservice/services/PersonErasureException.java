package ru.kondrashov.commonservice.services;

public class PersonErasureException extends RuntimeException {

    public PersonErasureException(String message) {
        super(message);
    }

    public PersonErasureException(String message, Throwable cause) {
        super(message, cause);
    }
}
