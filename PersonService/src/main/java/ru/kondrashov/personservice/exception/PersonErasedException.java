package ru.kondrashov.personservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.GONE)
public class PersonErasedException extends RuntimeException{

    public PersonErasedException(String message){
        super(message);
    }

}
