package com.expense.springboot.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** Preserves JSON error responses without exposing local paths or stack traces. */
@RestControllerAdvice(basePackages = "com.expense.springboot.api")
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException ex) { return ApiResponses.error(HttpStatus.BAD_REQUEST, ex.getMessage()); }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> missing(NoSuchElementException ex) { return ApiResponses.error(HttpStatus.NOT_FOUND, ex.getMessage()); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        Set<HttpMethod> methods = ex.getSupportedHttpMethods();
        String allowed = methods == null ? "" : methods.stream().map(HttpMethod::name).reduce((a, b) -> a + ", " + b).orElse("");
        String message = allowed.isEmpty() ? "Method not allowed." : "Method not allowed. Allowed methods: " + allowed + ".";
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, allowed)
                .contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(Map.of("error", message));
    }
    @ExceptionHandler(IOException.class)
    ResponseEntity<?> storageFailure(IOException ex) { return ApiResponses.error(HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed because of a server error."); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception ex) { return ApiResponses.error(HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed because of a server error."); }
}
