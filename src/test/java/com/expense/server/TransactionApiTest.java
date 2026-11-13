package com.expense.server;

import com.expense.model.Transaction;
import com.expense.storage.DataInitializer;
import com.expense.storage.JsonDataStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Exercises the transaction HTTP endpoints using the JDK HTTP client and temporary JSON files. */
public class TransactionApiTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("transaction-api-test-");
        AppServer server = null;
        try {
            DataInitializer.initialize(temporaryDirectory);
            server = new AppServer(0, temporaryDirectory);
            server.start();
            verifyEndpoints(server.getPort(), temporaryDirectory);
            System.out.println("Transaction API checks passed.");
        } finally {
            if (server != null) server.stop();
            try (var paths = Files.walk(temporaryDirectory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyEndpoints(int port, Path dataDirectory) throws Exception {
        String baseUrl = "http://127.0.0.1:" + port + "/api/transactions";

        HttpResponse<String> createdResponse = send("POST", baseUrl, """
                {"type":"EXPENSE","amount":125.12345678901234567890,
                 "category":"Education","date":"2026-11-06","description":"Course fee"}
                """);
        checkStatus(createdResponse, 201, "Add transaction");
        JsonObject created = objectBody(createdResponse);
        String firstId = created.get("id").getAsString();

        HttpResponse<String> secondCreatedResponse = send("POST", baseUrl, """
                {"type":"INCOME","amount":2400.00,
                 "category":"Other","date":"2026-11-07","description":"Salary"}
                """);
        checkStatus(secondCreatedResponse, 201, "Add second transaction");
        String secondId = objectBody(secondCreatedResponse).get("id").getAsString();
        check(!firstId.equals(secondId), "New transactions should get unique IDs.");

        HttpResponse<String> allResponse = send("GET", baseUrl, null);
        checkStatus(allResponse, 200, "List transactions");
        check(arrayBody(allResponse).size() == 2, "Listing should return both added transactions.");

        HttpResponse<String> oneResponse = send("GET", baseUrl + "/" + firstId, null);
        checkStatus(oneResponse, 200, "Get transaction by ID");
        check(firstId.equals(objectBody(oneResponse).get("id").getAsString()), "Single transaction ID did not match.");

        String updateBody = """
                {"id":"%s","type":"EXPENSE","amount":150.2500000000000000001,
                 "category":"Education","date":"2026-11-06","description":"Updated course fee"}
                """.formatted(firstId);
        HttpResponse<String> updatedResponse = send("PUT", baseUrl + "/" + firstId, updateBody);
        checkStatus(updatedResponse, 200, "Update transaction");
        check(objectBody(updatedResponse).get("amount").getAsBigDecimal()
                        .equals(new java.math.BigDecimal("150.2500000000000000001")),
                "Update should preserve the exact decimal amount.");

        HttpResponse<String> filteredResponse = send("GET", baseUrl
                + "?type=EXPENSE&category=Education&startDate=2026-11-01&endDate=2026-11-30&search=Updated", null);
        checkStatus(filteredResponse, 200, "Filter transactions");
        JsonArray filtered = arrayBody(filteredResponse);
        check(filtered.size() == 1 && firstId.equals(filtered.get(0).getAsJsonObject().get("id").getAsString()),
                "Type, category, date range, and search filters should select the updated expense.");

        HttpResponse<String> invalidAmountResponse = send("POST", baseUrl, """
                {"type":"EXPENSE","amount":0,"category":"Food","date":"2026-11-08","description":"Bad amount"}
                """);
        checkStatus(invalidAmountResponse, 400, "Reject invalid amount");
        check(objectBody(invalidAmountResponse).get("error").getAsString().contains("greater than zero"),
                "Invalid amount response should explain the validation error.");

        HttpResponse<String> invalidDateResponse = send("POST", baseUrl, """
                {"type":"EXPENSE","amount":1,"category":"Food","date":"2026-02-30","description":"Bad date"}
                """);
        checkStatus(invalidDateResponse, 400, "Reject invalid date");

        HttpResponse<String> invalidTypeResponse = send("POST", baseUrl, """
                {"type":"TRANSFER","amount":1,"category":"Food","date":"2026-11-08","description":"Bad type"}
                """);
        checkStatus(invalidTypeResponse, 400, "Reject invalid type");

        HttpResponse<String> duplicateIdResponse = send("POST", baseUrl, """
                {"id":"%s","type":"EXPENSE","amount":1,"category":"Food",
                 "date":"2026-11-08","description":"Duplicate ID"}
                """.formatted(firstId));
        checkStatus(duplicateIdResponse, 400, "Reject duplicate transaction ID");

        HttpResponse<String> missingResponse = send("GET", baseUrl + "/not-a-transaction", null);
        checkStatus(missingResponse, 404, "Missing transaction");
        HttpResponse<String> missingUpdate = send("PUT", baseUrl + "/not-a-transaction", """
                {"type":"EXPENSE","amount":1,"category":"Food","date":"2026-11-08","description":"Missing"}
                """);
        checkStatus(missingUpdate, 404, "Update missing transaction");
        HttpResponse<String> missingDelete = send("DELETE", baseUrl + "/not-a-transaction", null);
        checkStatus(missingDelete, 404, "Delete missing transaction");

        HttpResponse<String> deleteResponse = send("DELETE", baseUrl + "/" + secondId, null);
        checkStatus(deleteResponse, 200, "Delete transaction");
        check(secondId.equals(objectBody(deleteResponse).get("id").getAsString()), "Delete response ID did not match.");

        HttpResponse<String> unsupportedMethod = send("PATCH", baseUrl, "{}");
        checkStatus(unsupportedMethod, 405, "Reject unsupported method");

        List<Transaction> savedTransactions = new JsonDataStore(dataDirectory).readTransactions();
        check(savedTransactions.size() == 1, "transactions.json should contain the remaining transaction.");
        check(savedTransactions.get(0).getId().equals(firstId), "The saved transaction ID is incorrect.");
        check(Files.readString(dataDirectory.resolve("transactions.json")).contains("Updated course fee"),
                "The updated transaction should be present in transactions.json.");
    }

    private static HttpResponse<String> send(String method, String url, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url));
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject objectBody(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonArray arrayBody(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonArray();
    }

    private static void checkStatus(HttpResponse<String> response, int expected, String action) {
        check(response.statusCode() == expected,
                action + " returned " + response.statusCode() + " instead of " + expected + ": " + response.body());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
