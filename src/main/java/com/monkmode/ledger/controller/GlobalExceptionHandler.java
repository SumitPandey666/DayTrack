package com.monkmode.ledger.controller;

import com.monkmode.ledger.exception.LedgerValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LedgerValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(LedgerValidationException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}