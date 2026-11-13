package com.expense.server;

import com.expense.model.Transaction;
import com.expense.model.TransactionType;
import com.expense.service.TransactionService;
import com.expense.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** HTTP endpoints for creating, reading, editing, filtering, and deleting transactions. */
public class TransactionApiHandler implements HttpHandler {
    private static final String COLLECTION_PATH = "/api/transactions";
    private static final int MAX_REQUEST_BYTES = 64 * 1024;

    private final TransactionService transactionService;
    private final Gson gson = JsonUtil.createGson();

    public TransactionApiHandler(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String requestPath = exchange.getRequestURI().getPath();
            String id = getTransactionId(requestPath);
            String method = exchange.getRequestMethod();

            if (id == null) {
                handleCollectionRequest(exchange, method);
            } else {
                handleItemRequest(exchange, method, id);
            }
        } catch (RouteNotFoundException exception) {
            sendError(exchange, 404, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (NoSuchElementException exception) {
            sendError(exchange, 404, exception.getMessage());
        } catch (IOException exception) {
            // Keep local file paths and implementation details out of browser responses.
            sendError(exchange, 500, "The request could not be completed because of a server error.");
        } catch (Exception exception) {
            sendError(exchange, 500, "The request could not be completed because of a server error.");
        } finally {
            exchange.close();
        }
    }

    private void handleCollectionRequest(HttpExchange exchange, String method) throws IOException {
        switch (method) {
            case "GET" -> {
                TransactionFilters filters = parseFilters(exchange.getRequestURI().getRawQuery());
                sendJson(exchange, 200, transactionService.searchTransactions(
                        filters.type(), filters.category(), filters.startDate(), filters.endDate(), filters.search()));
            }
            case "POST" -> {
                Transaction created = transactionService.addTransaction(readTransaction(exchange));
                sendJson(exchange, 201, created);
            }
            default -> sendMethodNotAllowed(exchange, "GET, POST");
        }
    }

    private void handleItemRequest(HttpExchange exchange, String method, String id) throws IOException {
        switch (method) {
            case "GET" -> sendJson(exchange, 200, transactionService.findTransaction(id));
            case "PUT" -> sendJson(exchange, 200, transactionService.updateTransaction(id, readTransaction(exchange)));
            case "DELETE" -> {
                transactionService.deleteTransaction(id);
                JsonObject response = new JsonObject();
                response.addProperty("message", "Transaction deleted.");
                response.addProperty("id", id);
                sendJson(exchange, 200, response);
            }
            default -> sendMethodNotAllowed(exchange, "GET, PUT, DELETE");
        }
    }

    /** Returns null for the collection URL and a decoded ID for a single transaction URL. */
    private static String getTransactionId(String path) {
        if (COLLECTION_PATH.equals(path)) return null;
        String itemPrefix = COLLECTION_PATH + "/";
        if (!path.startsWith(itemPrefix)) {
            throw new RouteNotFoundException();
        }
        String id = path.substring(itemPrefix.length());
        if (id.isBlank() || id.contains("/")) {
            throw new RouteNotFoundException();
        }
        return id;
    }

    private static TransactionFilters parseFilters(String rawQuery) {
        Map<String, String> parameters = parseQuery(rawQuery);
        TransactionType type = null;
        String typeValue = parameters.get("type");
        if (typeValue != null) {
            try {
                type = TransactionType.valueOf(typeValue);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Type filter must be INCOME or EXPENSE.");
            }
        }

        String category = optionalNonEmpty(parameters.get("category"), "Category filter");
        LocalDate startDate = parseDateFilter(parameters.get("startDate"), "startDate");
        LocalDate endDate = parseDateFilter(parameters.get("endDate"), "endDate");
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must be on or before endDate.");
        }
        return new TransactionFilters(type, category, startDate, endDate, parameters.get("search"));
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> parameters = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return parameters;

        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            int equalsAt = pair.indexOf('=');
            String key = decode(equalsAt < 0 ? pair : pair.substring(0, equalsAt));
            String value = decode(equalsAt < 0 ? "" : pair.substring(equalsAt + 1));
            if (parameters.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Query parameter '" + key + "' can only be supplied once.");
            }
        }
        return parameters;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Query parameters contain invalid URL encoding.");
        }
    }

    private static String optionalNonEmpty(String value, String label) {
        if (value == null) return null;
        if (value.isBlank()) throw new IllegalArgumentException(label + " cannot be empty.");
        return value.trim();
    }

    private static LocalDate parseDateFilter(String value, String parameterName) {
        if (value == null) return null;
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException(parameterName + " must use YYYY-MM-DD format.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(parameterName + " must be a valid calendar date.");
        }
    }

    private Transaction readTransaction(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request body is too large.");
        }
        String requestBody = new String(bytes, StandardCharsets.UTF_8).trim();
        if (requestBody.isEmpty()) {
            throw new IllegalArgumentException("A JSON transaction object is required.");
        }

        final JsonElement json;
        try {
            json = JsonParser.parseString(requestBody);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Request body must contain valid JSON.");
        }
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Request body must be a JSON transaction object.");
        }

        JsonObject object = json.getAsJsonObject();
        String id = readString(object, "id", false);
        String typeText = readString(object, "type", true);
        TransactionType type = null;
        if (typeText != null) {
            try {
                type = TransactionType.valueOf(typeText);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Type must be INCOME or EXPENSE.");
            }
        }

        BigDecimal amount = readAmount(object.get("amount"));
        String category = readString(object, "category", true);
        LocalDate date = readDate(object.get("date"));
        String description = readString(object, "description", true);
        return new Transaction(id, type, amount, category, date, description);
    }

    private static String readString(JsonObject object, String property, boolean requiredToBeString) {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            if (requiredToBeString) {
                throw new IllegalArgumentException("'" + property + "' must be a string.");
            }
            throw new IllegalArgumentException("'" + property + "' must be a string when provided.");
        }
        return value.getAsString();
    }

    private static BigDecimal readAmount(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Amount must be a number greater than zero.");
        }
        try {
            return new BigDecimal(value.getAsString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Amount must be a valid decimal number.");
        }
    }

    private static LocalDate readDate(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Date must use YYYY-MM-DD format.");
        }
        String dateText = value.getAsString();
        if (!dateText.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Date must use YYYY-MM-DD format.");
        }
        try {
            return LocalDate.parse(dateText);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Date must be a valid calendar date.");
        }
    }

    private void sendMethodNotAllowed(HttpExchange exchange, String allowedMethods) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Allow", allowedMethods);
        sendError(exchange, 405, "Method not allowed. Allowed methods: " + allowedMethods + ".");
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message == null || message.isBlank() ? "Invalid request." : message);
        sendJson(exchange, statusCode, error);
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object value) throws IOException {
        byte[] body = gson.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
    }

    private record TransactionFilters(TransactionType type, String category, LocalDate startDate,
                                      LocalDate endDate, String search) { }

    /** A route miss is distinguished from a missing transaction ID. */
    private static class RouteNotFoundException extends IllegalArgumentException {
        private RouteNotFoundException() { super("API route not found."); }
    }
}
