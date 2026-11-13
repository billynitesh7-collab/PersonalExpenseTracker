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
import java.util.List;

/** Checks report calculations and request validation using temporary JSON files. */
public class ReportApiTest {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("expense-report-test-");
        AppServer server = null;
        try {
            DataInitializer.initialize(temporaryDirectory);
            seedTransactions(temporaryDirectory);
            String transactionsBefore = Files.readString(temporaryDirectory.resolve("transactions.json"));

            server = new AppServer(0, temporaryDirectory);
            server.start();
            verifyReports(server.getPort());
            check(Files.readString(temporaryDirectory.resolve("transactions.json")).equals(transactionsBefore),
                    "Report requests must not modify stored transactions.");

            server.stop();
            server = null;
            verifyEmptyReports(temporaryDirectory.resolveSibling(temporaryDirectory.getFileName() + "-empty"));
            System.out.println("Report API checks passed.");
        } finally {
            if (server != null) server.stop();
            deleteDirectoryIfPresent(temporaryDirectory);
            deleteDirectoryIfPresent(temporaryDirectory.resolveSibling(temporaryDirectory.getFileName() + "-empty"));
        }
    }

    private static void seedTransactions(Path dataDirectory) throws Exception {
        JsonDataStore store = new JsonDataStore(dataDirectory);
        store.saveTransactions(List.of(
                transaction("expense-food", TransactionType.EXPENSE, "10.10", "Food", "2026-11-06"),
                transaction("income-salary", TransactionType.INCOME, "100.01", "Other", "2026-11-06"),
                transaction("expense-travel", TransactionType.EXPENSE, "30.30", "Travel", "2026-11-15"),
                transaction("expense-bills", TransactionType.EXPENSE, "5.25", "Bills", "2026-10-31"),
                transaction("income-old", TransactionType.INCOME, "1.25", "Other", "2025-01-01")
        ));
    }

    private static Transaction transaction(String id, TransactionType type, String amount,
                                           String category, String date) {
        return new Transaction(id, type, new BigDecimal(amount), category,
                LocalDate.parse(date), "Report test transaction");
    }

    private static void verifyReports(int port) throws Exception {
        String base = "http://127.0.0.1:" + port + "/api/reports";

        JsonObject summary = object(get(base + "/summary", 200));
        decimal(summary, "totalIncome", "101.26");
        decimal(summary, "totalExpenses", "45.65");
        decimal(summary, "netSavings", "55.61");
        check(summary.get("totalTransactions").getAsLong() == 5, "Summary transaction count should be five.");

        JsonObject daily = object(get(base + "/daily?date=2026-11-06", 200));
        decimal(daily, "dailyIncome", "100.01");
        decimal(daily, "dailyExpenses", "10.10");
        decimal(daily, "dailySavings", "89.91");
        check(daily.get("transactionCount").getAsLong() == 2, "Daily transaction count should be two.");

        JsonObject monthly = object(get(base + "/monthly?month=2026-11", 200));
        decimal(monthly, "monthlyIncome", "100.01");
        decimal(monthly, "monthlyExpenses", "40.40");
        decimal(monthly, "monthlySavings", "59.61");
        JsonArray monthlyComparison = monthly.getAsJsonArray("monthlyComparison");
        check(monthlyComparison.size() == 12, "Monthly comparison should include all twelve months.");
        JsonObject emptyMonth = monthlyComparison.get(0).getAsJsonObject();
        decimal(emptyMonth, "monthlyIncome", "0");
        decimal(emptyMonth, "monthlyExpenses", "0");

        JsonObject yearly = object(get(base + "/yearly?year=2026", 200));
        decimal(yearly, "yearlyIncome", "100.01");
        decimal(yearly, "yearlyExpenses", "45.65");
        decimal(yearly, "yearlySavings", "54.36");
        check(yearly.get("transactionCount").getAsLong() == 4, "Yearly transaction count should be four.");
        check(yearly.getAsJsonArray("monthlyBreakdown").size() == 12,
                "Yearly report should include a twelve-month breakdown.");

        JsonObject categories = object(get(base + "/categories?month=2026-11", 200));
        decimal(categories, "totalExpenses", "40.40");
        check(categories.get("highestSpendingCategory").getAsString().equals("Travel"),
                "Travel should be the highest spending category.");
        check(categories.get("lowestSpendingCategory").getAsString().equals("Food"),
                "Food should be the lowest spending category with expenses.");
        JsonArray categoryList = categories.getAsJsonArray("categories");
        check(categoryList.size() == 2, "Income transactions must be excluded from category analytics.");
        decimal(categoryList.get(0).getAsJsonObject(), "percentage", "75.00");
        decimal(categoryList.get(1).getAsJsonObject(), "percentage", "25.00");
        decimal(categoryList.get(0).getAsJsonObject(), "totalExpenses", "30.30");

        get(base + "/daily?date=2026-02-30", 400);
        get(base + "/daily?date=2026-2-01", 400);
        get(base + "/monthly?month=2026-13", 400);
        get(base + "/monthly?month=2026-1", 400);
        get(base + "/yearly?year=0000", 400);
        get(base + "/yearly?year=26", 400);
        get(base + "/categories?month=2026-11&extra=1", 400);
        get(base + "/unknown", 404);
        send("POST", base + "/summary", null, 405);
    }

    private static void verifyEmptyReports(Path directory) throws Exception {
        DataInitializer.initialize(directory);
        AppServer emptyServer = new AppServer(0, directory);
        try {
            emptyServer.start();
            String base = "http://127.0.0.1:" + emptyServer.getPort() + "/api/reports";
            JsonObject summary = object(get(base + "/summary", 200));
            decimal(summary, "totalIncome", "0");
            decimal(summary, "totalExpenses", "0");
            decimal(summary, "netSavings", "0");
            check(summary.get("totalTransactions").getAsLong() == 0, "Empty summary count should be zero.");

            JsonObject daily = object(get(base + "/daily?date=2020-01-01", 200));
            decimal(daily, "dailySavings", "0");
            JsonObject monthly = object(get(base + "/monthly?month=2020-01", 200));
            decimal(monthly, "monthlyExpenses", "0");
            JsonObject yearly = object(get(base + "/yearly?year=2020", 200));
            decimal(yearly, "yearlySavings", "0");
            JsonObject categories = object(get(base + "/categories?month=2020-01", 200));
            decimal(categories, "totalExpenses", "0");
            check(categories.getAsJsonArray("categories").isEmpty(), "Empty category results should be an empty array.");
            check(categories.get("highestSpendingCategory").isJsonNull(), "Empty reports should have no highest category.");
            check(categories.get("lowestSpendingCategory").isJsonNull(), "Empty reports should have no lowest category.");
        } finally {
            emptyServer.stop();
        }
    }

    private static HttpResponse<String> get(String url, int expectedStatus) throws Exception {
        return send("GET", url, null, expectedStatus);
    }

    private static HttpResponse<String> send(String method, String url, String body, int expectedStatus) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url));
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == expectedStatus,
                method + " " + url + " returned " + response.statusCode() + ", expected " + expectedStatus + ": " + response.body());
        return response;
    }

    private static JsonObject object(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void decimal(JsonObject object, String field, String expected) {
        check(object.get(field).getAsBigDecimal().compareTo(new BigDecimal(expected)) == 0,
                field + " should equal " + expected + ", got " + object.get(field));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteDirectoryIfPresent(Path directory) throws Exception {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
