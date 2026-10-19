package ru.kondrashov.accountservice.exceptions;

public class BillUpdateNotAllowedException extends RuntimeException{

    public BillUpdateNotAllowedException(String message){super(message);}
}
