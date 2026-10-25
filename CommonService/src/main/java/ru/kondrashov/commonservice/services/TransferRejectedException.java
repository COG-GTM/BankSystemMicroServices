package ru.kondrashov.commonservice.services;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TransferRejectedException extends RuntimeException {

    private final HttpStatus status;

    public TransferRejectedException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
