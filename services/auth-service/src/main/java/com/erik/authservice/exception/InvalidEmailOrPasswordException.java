package com.erik.authservice.exception;

public class InvalidEmailOrPasswordException extends RuntimeException{
    public InvalidEmailOrPasswordException(String message) {
        super(message);
    }
}
