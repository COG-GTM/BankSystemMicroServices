package ru.kondrashov.accountservice.exceptions;

public class AccountHasNonZeroBalanceException extends RuntimeException{

    public AccountHasNonZeroBalanceException(String message){
        super(message);
    }
}
