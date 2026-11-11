package com.stitchstyle.app.exception;

public class InvalidChatException extends RuntimeException {
    private final String code;

    public InvalidChatException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
