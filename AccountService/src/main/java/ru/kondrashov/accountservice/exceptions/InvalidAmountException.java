package ru.kondrashov.accountservice.exceptions;

public class InvalidAmountException extends RuntimeException{

    public InvalidAmountException(String message){super(message);}
}
