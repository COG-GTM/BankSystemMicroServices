package ru.kondrashov.accountservice.exceptions;

public class BillUpdateRejectedException extends RuntimeException {

    public BillUpdateRejectedException(String message) {
        super(message);
    }
}
