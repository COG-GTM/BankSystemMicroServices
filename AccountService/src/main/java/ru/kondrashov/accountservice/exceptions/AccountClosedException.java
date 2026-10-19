package ru.kondrashov.accountservice.exceptions;

public class AccountClosedException extends RuntimeException {

    public AccountClosedException(String s) {
        super(s);
    }
}
