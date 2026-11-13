package com.expense.server;

import com.expense.model.BudgetSummary;
import com.expense.service.BudgetService;
import com.expense.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/** HTTP endpoints for monthly budget creation, retrieval, and updates. */
public class BudgetApiHandler implements HttpHandler {
    private static final String COLLECTION_PATH = "/api/budgets";
    private static final int MAX_REQUEST_BYTES = 64 * 1024;

    private final BudgetService budgetService;
    private final Gson gson = JsonUtil.createGson().newBuilder().serializeNulls().create();

    public BudgetApiHandler(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (COLLECTION_PATH.equals(path)) {
                handleCollectionRequest(exchange, method);
            } else if (path.startsWith(COLLECTION_PATH + "/")) {
                handleMonthRequest(exchange, method, path.substring(COLLECTION_PATH.length() + 1));
            } else {
                throw new RouteNotFoundException();
            }
        } catch (RouteNotFoundException exception) {
            sendError(exchange, 404, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (IOException exception) {
            // Do not expose local filenames or implementation details to the browser.
            sendError(exchange, 500, "The request could not be completed because of a server error.");
        } catch (Exception exception) {
            sendError(exchange, 500, "The request could not be completed because of a server error.");
        } finally {
            exchange.close();
        }
    }

    private void handleCollectionRequest(HttpExchange exchange, String method) throws IOException {
        if (!"GET".equals(method)) {
            sendMethodNotAllowed(exchange, "GET");
            return;
        }

        String monthValue = parseMonthQuery(exchange.getRequestURI().getRawQuery());
        if (monthValue == null) {
            sendJson(exchange, 200, budgetService.getAllBudgetSummaries());
        } else {
            sendJson(exchange, 200, budgetService.getBudgetSummary(parseMonth(monthValue, "month")));
        }
    }

    private void handleMonthRequest(HttpExchange exchange, String method, String monthValue) throws IOException {
        if (!"PUT".equals(method)) {
            sendMethodNotAllowed(exchange, "PUT");
            return;
        }
        YearMonth month = parseMonth(monthValue, "Month in the URL");
        BigDecimal amount = readAmount(exchange);
        budgetService.createOrUpdateBudget(month, amount);
        sendJson(exchange, 200, budgetService.getBudgetSummary(month));
    }

    private static String parseMonthQuery(String rawQuery) {
        Map<String, String> parameters = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return null;

        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            int equalsAt = pair.indexOf('=');
            String key = decode(equalsAt < 0 ? pair : pair.substring(0, equalsAt));
            String value = decode(equalsAt < 0 ? "" : pair.substring(equalsAt + 1));
            if (!"month".equals(key)) {
                throw new IllegalArgumentException("Only the 'month' query parameter is supported.");
            }
            if (parameters.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("The 'month' query parameter can only be supplied once.");
            }
        }
        String month = parameters.get("month");
        if (month != null && month.isBlank()) {
            throw new IllegalArgumentException("Month cannot be empty.");
        }
        return month;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Query parameters contain invalid URL encoding.");
        }
    }

    private static YearMonth parseMonth(String value, String label) {
        if (value == null || !value.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException(label + " must use YYYY-MM format.");
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(label + " must be a valid month.");
        }
    }

    private static BigDecimal readAmount(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request body is too large.");
        }
        String body = new String(bytes, StandardCharsets.UTF_8).trim();
        if (body.isEmpty()) throw new IllegalArgumentException("A JSON budget object is required.");

        final JsonElement json;
        try {
            json = JsonParser.parseString(body);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Request body must contain valid JSON.");
        }
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Request body must be a JSON budget object.");
        }

        JsonElement amountElement = json.getAsJsonObject().get("amount");
        if (amountElement == null || !amountElement.isJsonPrimitive()
                || !amountElement.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Budget amount must be a number greater than zero.");
        }
        try {
            return new BigDecimal(amountElement.getAsString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Budget amount must be a valid decimal number.");
        }
    }

    private void sendMethodNotAllowed(HttpExchange exchange, String allowedMethods) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowedMethods);
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

    private static class RouteNotFoundException extends IllegalArgumentException {
        private RouteNotFoundException() { super("API route not found."); }
    }
}
