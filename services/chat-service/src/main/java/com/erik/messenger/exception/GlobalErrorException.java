package com.erik.messenger.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerAdvice
public class GlobalErrorException {
    private static final Logger logger = LoggerFactory.getLogger(GlobalErrorException.class);

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<String> runTimeExceptionHandler(RuntimeException ex) {
        logger.error("Internal Server Error: ", ex);
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> illegalArgumentExceptionHandler(IllegalArgumentException ex) {
        logger.warn("Bad Request: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<String> notFoundException(NotFoundException ex) {
        logger.warn("Not Found: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    ResponseEntity<String> missingRequestHeaderExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException ex) {
        logger.warn("Missing Header: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(io.jsonwebtoken.JwtException.class)
    ResponseEntity<String> jwtExceptionHandler(io.jsonwebtoken.JwtException ex) {
        logger.warn("JWT Error: {}", ex.getMessage());
        return new ResponseEntity<>("Invalid or expired token: " + ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    // You can add a specific handler for access denied situations if needed
    // e.g. for security-related RuntimeExceptions from the service layer
}
