package com.reliaquest.api.exception;

public class ExternalApiException extends RuntimeException {
    private final int status;

    public ExternalApiException(String message, int status) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
