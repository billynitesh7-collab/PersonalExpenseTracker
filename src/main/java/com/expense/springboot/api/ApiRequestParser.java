package com.expense.springboot.api;

import com.expense.model.Transaction;
import com.expense.model.TransactionType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/** Mirrors the validation performed by the existing HttpServer API handlers. */
final class ApiRequestParser {
    private static final int MAX_REQUEST_BYTES = 64 * 1024;
    private ApiRequestParser() { }

    static Transaction readTransaction(String body) {
        JsonObject object = readObject(body, "A JSON transaction object is required.", "Request body must be a JSON transaction object.");
        String id = readString(object, "id", false);
        String typeText = readString(object, "type", true);
        TransactionType type = null;
        if (typeText != null) {
            try { type = TransactionType.valueOf(typeText); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Type must be INCOME or EXPENSE."); }
        }
        BigDecimal amount = readAmount(object.get("amount"), "Amount must be a number greater than zero.", "Amount must be a valid decimal number.");
        String category = readString(object, "category", true);
        LocalDate date = readDate(object.get("date"));
        String description = readString(object, "description", true);
        return new Transaction(id, type, amount, category, date, description);
    }

    static BigDecimal readBudgetAmount(String body) {
        JsonObject object = readObject(body, "A JSON budget object is required.", "Request body must be a JSON budget object.");
        return readAmount(object.get("amount"), "Budget amount must be a number greater than zero.", "Budget amount must be a valid decimal number.");
    }

    private static JsonObject readObject(String body, String emptyMessage, String typeMessage) {
        if (body == null || body.trim().isEmpty()) throw new IllegalArgumentException(emptyMessage);
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) throw new IllegalArgumentException("Request body is too large.");
        final JsonElement json;
        try { json = JsonParser.parseString(body.trim()); }
        catch (JsonParseException exception) { throw new IllegalArgumentException("Request body must contain valid JSON."); }
        if (!json.isJsonObject()) throw new IllegalArgumentException(typeMessage);
        return json.getAsJsonObject();
    }

    private static String readString(JsonObject object, String name, boolean requiredToBeString) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            if (requiredToBeString) throw new IllegalArgumentException("'" + name + "' must be a string.");
            throw new IllegalArgumentException("'" + name + "' must be a string when provided.");
        }
        return value.getAsString();
    }

    private static BigDecimal readAmount(JsonElement value, String missingMessage, String invalidMessage) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(missingMessage);
        try { return new BigDecimal(value.getAsString()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException(invalidMessage); }
    }

    private static LocalDate readDate(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Date must use YYYY-MM-DD format.");
        String text = value.getAsString();
        if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Date must use YYYY-MM-DD format.");
        try { return LocalDate.parse(text); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException("Date must be a valid calendar date."); }
    }

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return result;
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            int equalsAt = pair.indexOf('=');
            String key = decode(equalsAt < 0 ? pair : pair.substring(0, equalsAt));
            String value = decode(equalsAt < 0 ? "" : pair.substring(equalsAt + 1));
            if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Query parameter '" + key + "' can only be supplied once.");
        }
        return result;
    }

    static Map<String, String> parseOnlyQuery(String rawQuery, String name) {
        Map<String, String> params = parseQuery(rawQuery);
        if (params.size() != 1 || !params.containsKey(name)) throw new IllegalArgumentException("This endpoint requires only the '" + name + "' query parameter.");
        if (params.get(name).isBlank()) throw new IllegalArgumentException("The '" + name + "' query parameter cannot be empty.");
        return params;
    }

    static void requireOnlyAllowedQuery(Map<String, String> params, String allowed) {
        for (String name : params.keySet()) if (!name.equals(allowed)) throw new IllegalArgumentException("Only the '" + allowed + "' query parameter is supported.");
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Query parameters contain invalid URL encoding."); }
    }

    static String nonEmptyTrimmed(String value, String label) {
        if (value == null) return null;
        if (value.isBlank()) throw new IllegalArgumentException(label + " cannot be empty.");
        return value.trim();
    }

    static LocalDate parseDateFilter(String value, String name) {
        if (value == null) return null;
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException(name + " must use YYYY-MM-DD format.");
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException(name + " must be a valid calendar date."); }
    }

    static YearMonth parseMonth(String value, String label) {
        if (value == null || !value.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException(label + " must use YYYY-MM format.");
        try { return YearMonth.parse(value); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException(label + " must be a valid month."); }
    }

    static Year parseYear(String value) {
        if (value == null || !value.matches("\\d{4}")) throw new IllegalArgumentException("Year must use YYYY format.");
        try {
            int year = Integer.parseInt(value);
            if (year < 1 || year > 9999) throw new NumberFormatException();
            return Year.of(year);
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw new IllegalArgumentException("Year must be a valid year from 0001 to 9999.");
        }
    }

    static TransactionFilters parseTransactionFilters(String rawQuery) {
        Map<String, String> params = parseQuery(rawQuery);
        TransactionType type = null;
        if (params.get("type") != null) {
            try { type = TransactionType.valueOf(params.get("type")); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Type filter must be INCOME or EXPENSE."); }
        }
        String category = nonEmptyTrimmed(params.get("category"), "Category filter");
        LocalDate start = parseDateFilter(params.get("startDate"), "startDate");
        LocalDate end = parseDateFilter(params.get("endDate"), "endDate");
        if (start != null && end != null && start.isAfter(end)) throw new IllegalArgumentException("startDate must be on or before endDate.");
        return new TransactionFilters(type, category, start, end, params.get("search"));
    }

    record TransactionFilters(TransactionType type, String category, LocalDate startDate, LocalDate endDate, String search) { }
}
