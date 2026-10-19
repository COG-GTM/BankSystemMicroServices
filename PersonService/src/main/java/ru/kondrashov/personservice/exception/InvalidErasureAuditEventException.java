package ru.kondrashov.personservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class InvalidErasureAuditEventException extends RuntimeException{

    public InvalidErasureAuditEventException(String message){
        super(message);
    }
}
