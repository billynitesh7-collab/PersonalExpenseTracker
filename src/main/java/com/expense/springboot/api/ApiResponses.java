package com.expense.springboot.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.Map;

final class ApiResponses {
    private ApiResponses() { }
    static <T> ResponseEntity<T> json(HttpStatusCode status, T body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(body);
    }
    static ResponseEntity<Map<String, String>> error(HttpStatusCode status, String message) {
        return json(status, Map.of("error", message == null || message.isBlank() ? "Invalid request." : message));
    }
    static ResponseEntity<Map<String, String>> methodNotAllowed(String allowed) {
        return ResponseEntity.status(405).header("Allow", allowed).contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore()).body(Map.of("error", "Method not allowed. Allowed methods: " + allowed + "."));
    }
}
