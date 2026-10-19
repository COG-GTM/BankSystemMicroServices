package ru.kondrashov.personservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class PersonHasOpenAccountsException extends RuntimeException{

    public PersonHasOpenAccountsException(String message){
        super(message);
    }
}
