package ru.kondrashov.accountservice.exceptions;

public class InvalidPageRequestException extends RuntimeException {

    public InvalidPageRequestException(String s) {
        super(s);
    }
}
