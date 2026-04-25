package com.erik.authservice.exception;

import java.util.Map;

public class UserAlreadyExistsException extends RuntimeException {
    private final Map<String, String> errors;

    public UserAlreadyExistsException(Map<String, String> errors) {
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}