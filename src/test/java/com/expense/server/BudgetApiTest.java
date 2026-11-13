package com.expense.server;

import com.expense.model.Transaction;
import com.expense.model.TransactionType;
import com.expense.storage.DataInitializer;
import com.expense.storage.JsonDataStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;

/** Exercises budget endpoints with isolated files and verifies persistence across server restarts. */
public class BudgetApiTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("budget-api-test-");
        AppServer server = null;
        try {
            DataInitializer.initialize(temporaryDirectory);
            seedTransactions(temporaryDirectory);

            server = new AppServer(0, temporaryDirectory);
            server.start();
            int port = server.getPort();
            verifyBudgetEndpoints(port, temporaryDirectory);

            server.stop();
            server = new AppServer(0, temporaryDirectory);
            server.start();
            verifyPersistence(server.getPort());

            System.out.println("Budget API checks passed.");
        } finally {
            if (server != null) server.stop();
            try (var paths = Files.walk(temporaryDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void seedTransactions(Path dataDirectory) throws Exception {
        JsonDataStore store = new JsonDataStore(dataDirectory);
        store.saveTransactions(java.util.List.of(
                transaction("expense-a", TransactionType.EXPENSE, "100.10", "2026-11-04"),
                transaction("expense-b", TransactionType.EXPENSE, "50.65", "2026-11-20"),
                transaction("income-a", TransactionType.INCOME, "2000.00", "2026-11-01"),
                transaction("expense-oct", TransactionType.EXPENSE, "50.00", "2026-10-31")
        ));
    }

    private static Transaction transaction(String id, TransactionType type, String amount, String date) {
        return new Transaction(id, type, new BigDecimal(amount), "Food",
                LocalDate.parse(date), "Budget test transaction");
    }

    private static void verifyBudgetEndpoints(int port, Path dataDirectory) throws Exception {
        String baseUrl = "http://127.0.0.1:" + port + "/api/budgets";

        HttpResponse<String> created = send("PUT", baseUrl + "/2026-11", "{\"amount\":200.00}");
        checkStatus(created, 200, "Create monthly budget");
        JsonObject createdSummary = objectBody(created);
        checkDecimal(createdSummary, "budgetAmount", "200.00");
        checkDecimal(createdSummary, "totalExpenses", "150.75");
        checkDecimal(createdSummary, "remainingBudget", "49.25");
        checkDecimal(createdSummary, "usagePercentage", "75.38");
        check(!createdSummary.get("exceeded").getAsBoolean(), "Budget should not be exceeded yet.");

        HttpResponse<String> specific = send("GET", baseUrl + "?month=2026-11", null);
        checkStatus(specific, 200, "Retrieve one month's budget");
        checkDecimal(objectBody(specific), "remainingBudget", "49.25");

        HttpResponse<String> updated = send("PUT", baseUrl + "/2026-11", "{\"amount\":100.00}");
        checkStatus(updated, 200, "Update existing monthly budget");
        JsonObject updatedSummary = objectBody(updated);
        checkDecimal(updatedSummary, "remainingBudget", "-50.75");
        checkDecimal(updatedSummary, "usagePercentage", "150.75");
        check(updatedSummary.get("exceeded").getAsBoolean(), "Overspending should be detected.");

        HttpResponse<String> allBudgets = send("GET", baseUrl, null);
        checkStatus(allBudgets, 200, "Retrieve all budgets");
        JsonArray budgetList = arrayBody(allBudgets);
        check(budgetList.size() == 1, "Updating a month must not create a duplicate budget.");

        HttpResponse<String> missingBudget = send("GET", baseUrl + "?month=2026-10", null);
        checkStatus(missingBudget, 200, "Retrieve a month with no budget");
        JsonObject missingSummary = objectBody(missingBudget);
        check(missingSummary.get("budgetAmount").isJsonNull(), "Missing budget amount should be null.");
        checkDecimal(missingSummary, "totalExpenses", "50.00");
        check(missingSummary.get("remainingBudget").isJsonNull(), "Missing budget should have no remaining amount.");
        check(missingSummary.get("usagePercentage").isJsonNull(), "Missing budget should have no usage percentage.");
        check(!missingSummary.get("exceeded").getAsBoolean(), "A missing budget is not marked exceeded.");

        HttpResponse<String> invalidAmount = send("PUT", baseUrl + "/2026-12", "{\"amount\":0}");
        checkStatus(invalidAmount, 400, "Reject zero budget amount");
        HttpResponse<String> negativeAmount = send("PUT", baseUrl + "/2026-12", "{\"amount\":-1}");
        checkStatus(negativeAmount, 400, "Reject negative budget amount");
        HttpResponse<String> invalidJson = send("PUT", baseUrl + "/2026-12", "{invalid");
        checkStatus(invalidJson, 400, "Reject invalid budget JSON");

        HttpResponse<String> invalidMonthInUrl = send("PUT", baseUrl + "/2026-13", "{\"amount\":10}");
        checkStatus(invalidMonthInUrl, 400, "Reject an invalid month in the URL");
        HttpResponse<String> invalidMonthFormat = send("GET", baseUrl + "?month=2026-1", null);
        checkStatus(invalidMonthFormat, 400, "Reject an incorrectly formatted month");

        String persistedJson = Files.readString(dataDirectory.resolve("budgets.json"));
        check(persistedJson.contains("2026-11") && persistedJson.contains("100.00"),
                "The updated budget should be saved in budgets.json.");
    }

    private static void verifyPersistence(int port) throws Exception {
        HttpResponse<String> response = send("GET", "http://127.0.0.1:" + port
                + "/api/budgets?month=2026-11", null);
        checkStatus(response, 200, "Read budget after restarting the server");
        JsonObject summary = objectBody(response);
        checkDecimal(summary, "budgetAmount", "100.00");
        checkDecimal(summary, "totalExpenses", "150.75");
        check(summary.get("exceeded").getAsBoolean(), "Overspending status should persist after restart.");
    }

    private static HttpResponse<String> send(String method, String url, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject objectBody(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static JsonArray arrayBody(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonArray();
    }

    private static void checkDecimal(JsonObject object, String property, String expected) {
        check(object.get(property).getAsBigDecimal().compareTo(new BigDecimal(expected)) == 0,
                property + " should equal " + expected + ".");
    }

    private static void checkStatus(HttpResponse<String> response, int expected, String action) {
        check(response.statusCode() == expected,
                action + " returned " + response.statusCode() + " instead of " + expected + ": " + response.body());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
