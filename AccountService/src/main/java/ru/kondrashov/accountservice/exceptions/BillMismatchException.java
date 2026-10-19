package ru.kondrashov.accountservice.exceptions;

public class BillMismatchException extends RuntimeException {

    public BillMismatchException(String s) {
        super(s);
    }
}
