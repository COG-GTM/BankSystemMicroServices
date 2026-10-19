package ru.kondrashov.accountservice.exceptions;

public class BillClosedException extends RuntimeException {

    public BillClosedException(String s) {
        super(s);
    }
}
