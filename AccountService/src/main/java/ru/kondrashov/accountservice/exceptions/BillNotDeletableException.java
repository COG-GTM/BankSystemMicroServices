package ru.kondrashov.accountservice.exceptions;

public class BillNotDeletableException extends RuntimeException{

    public BillNotDeletableException(String message){super(message);}
}
