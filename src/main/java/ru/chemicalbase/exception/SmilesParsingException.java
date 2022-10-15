package ru.chemicalbase.exception;

public class SmilesParsingException extends RuntimeException {
    public SmilesParsingException(String message) {
        super(message);
    }
}
