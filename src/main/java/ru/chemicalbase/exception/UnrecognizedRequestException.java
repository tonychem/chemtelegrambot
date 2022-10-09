package ru.chemicalbase.exception;

public class UnrecognizedRequestException extends RuntimeException {
    public UnrecognizedRequestException(String message) {
        super(message);
    }
}
