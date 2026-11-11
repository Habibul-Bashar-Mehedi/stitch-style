package com.stitchstyle.app.exception;

import java.util.List;

public class InsufficientStockException extends RuntimeException {
    private final List<ItemError> errors;

    public InsufficientStockException(String message, List<ItemError> errors) {
        super(message);
        this.errors = errors;
    }

    public List<ItemError> getErrors() { return errors; }

    public record ItemError(String field, String message) {}
}
