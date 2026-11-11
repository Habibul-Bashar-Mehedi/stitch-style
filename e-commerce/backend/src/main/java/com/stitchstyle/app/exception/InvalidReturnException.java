package com.stitchstyle.app.exception;

public class InvalidReturnException extends RuntimeException {
    private final String code;

    public InvalidReturnException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
