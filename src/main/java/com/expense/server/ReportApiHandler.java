package com.expense.server;

import com.expense.service.ReportService;
import com.expense.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/** Read-only HTTP endpoints for reports and analytics. */
public class ReportApiHandler implements HttpHandler {
    private static final String BASE_PATH = "/api/reports";
    private final ReportService reportService;
    private final Gson gson = JsonUtil.createGson().newBuilder().serializeNulls().create();

    public ReportApiHandler(ReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendError(exchange, 405, "Method not allowed. Allowed method: GET.");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String query = exchange.getRequestURI().getRawQuery();
            Object result;
            switch (path) {
                case BASE_PATH + "/summary" -> {
                    requireNoQuery(query);
                    result = reportService.getFinancialSummary();
                }
                case BASE_PATH + "/daily" -> result = reportService.getDailyReport(parseDate(requiredQuery(query, "date")));
                case BASE_PATH + "/monthly" -> result = reportService.getMonthlyReport(parseMonth(requiredQuery(query, "month")));
                case BASE_PATH + "/yearly" -> result = reportService.getYearlyReport(parseYear(requiredQuery(query, "year")));
                case BASE_PATH + "/categories" -> result = reportService.getCategoryReport(parseMonth(requiredQuery(query, "month")));
                default -> {
                    sendError(exchange, 404, "Report endpoint not found.");
                    return;
                }
            }
            sendJson(exchange, 200, result);
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (IOException exception) {
            sendError(exchange, 500, "The report could not be generated because of a server error.");
        } catch (Exception exception) {
            sendError(exchange, 500, "The report could not be generated because of a server error.");
        } finally {
            exchange.close();
        }
    }

    private static String requiredQuery(String rawQuery, String requiredName) {
        Map<String, String> parameters = parseQuery(rawQuery);
        if (parameters.size() != 1 || !parameters.containsKey(requiredName)) {
            throw new IllegalArgumentException("This endpoint requires only the '" + requiredName + "' query parameter.");
        }
        String value = parameters.get(requiredName);
        if (value.isBlank()) throw new IllegalArgumentException("The '" + requiredName + "' query parameter cannot be empty.");
        return value;
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

    private static void requireNoQuery(String rawQuery) {
        if (!parseQuery(rawQuery).isEmpty()) {
            throw new IllegalArgumentException("The summary endpoint does not accept query parameters.");
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Query parameters contain invalid URL encoding.");
        }
    }

    private static LocalDate parseDate(String value) {
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Date must use YYYY-MM-DD format.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Date must be a valid calendar date.");
        }
    }

    private static YearMonth parseMonth(String value) {
        if (!value.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("Month must use YYYY-MM format.");
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Month must be a valid calendar month.");
        }
    }

    private static Year parseYear(String value) {
        if (!value.matches("\\d{4}")) {
            throw new IllegalArgumentException("Year must use YYYY format.");
        }
        try {
            int year = Integer.parseInt(value);
            if (year < 1 || year > 9999) {
                throw new IllegalArgumentException("Year must be a valid year from 0001 to 9999.");
            }
            return Year.of(year);
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw new IllegalArgumentException("Year must be a valid year from 0001 to 9999.");
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("error", message == null || message.isBlank() ? "Invalid request." : message);
        sendJson(exchange, statusCode, body);
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object value) throws IOException {
        byte[] body = gson.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
    }
}
