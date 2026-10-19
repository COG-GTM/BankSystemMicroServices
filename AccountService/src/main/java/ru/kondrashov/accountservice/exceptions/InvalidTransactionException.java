package ru.kondrashov.accountservice.exceptions;

public class InvalidTransactionException extends RuntimeException{

    public InvalidTransactionException(String message){
        super(message);
    }
}
