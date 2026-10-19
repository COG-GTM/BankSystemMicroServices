package ru.kondrashov.personservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
public class ErasurePreconditionUnavailableException extends RuntimeException{

    public ErasurePreconditionUnavailableException(String message){
        super(message);
    }
}
