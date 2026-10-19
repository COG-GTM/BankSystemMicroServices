package ru.kondrashov.accountservice.exceptions;

public class AccountClosedException extends RuntimeException{

    public AccountClosedException(String message){super(message);}
}
