package ru.kondrashov.personservice.crypto;

public class PiiEncryptionException extends RuntimeException {

    public PiiEncryptionException(String message) {
        super(message);
    }

    public PiiEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
