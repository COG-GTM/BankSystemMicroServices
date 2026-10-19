package ru.kondrashov.accountservice.exceptions;

public class PersonMismatchException extends RuntimeException{

    public PersonMismatchException(String message){
        super(message);
    }
}
