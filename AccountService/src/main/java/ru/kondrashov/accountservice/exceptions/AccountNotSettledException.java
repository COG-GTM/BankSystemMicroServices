package ru.kondrashov.accountservice.exceptions;

public class AccountNotSettledException extends RuntimeException{

    public AccountNotSettledException(String message){super(message);}
}
