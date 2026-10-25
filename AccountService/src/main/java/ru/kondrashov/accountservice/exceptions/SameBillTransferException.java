package ru.kondrashov.accountservice.exceptions;

public class SameBillTransferException extends RuntimeException{

    public SameBillTransferException(String message){super(message);}
}
