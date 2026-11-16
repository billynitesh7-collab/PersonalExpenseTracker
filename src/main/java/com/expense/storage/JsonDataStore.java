package com.expense.storage;

import com.expense.model.Budget;
import com.expense.model.Transaction;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.expense.util.JsonUtil;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reads and writes the two JSON files used by the application. */
public class JsonDataStore implements FinanceDataStore {
    private static final Type TRANSACTION_LIST_TYPE = new TypeToken<List<Transaction>>() { }.getType();
    private static final Type BUDGET_LIST_TYPE = new TypeToken<List<Budget>>() { }.getType();

    private final Path dataDirectory;
    private final Path transactionsFile;
    private final Path budgetsFile;
    private final Gson gson;

    public JsonDataStore(Path dataDirectory) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.transactionsFile = this.dataDirectory.resolve("transactions.json");
        this.budgetsFile = this.dataDirectory.resolve("budgets.json");

        // Store dates as readable ISO strings: 2026-11-06 and 2026-11.
        this.gson = JsonUtil.createGson();
    }

    public List<Transaction> readTransactions() throws IOException {
        return readList(transactionsFile, TRANSACTION_LIST_TYPE, "transactions");
    }

    public void saveTransactions(List<Transaction> transactions) throws IOException {
        writeList(transactionsFile, Objects.requireNonNull(transactions, "transactions"));
    }

    public List<Budget> readBudgets() throws IOException {
        return readList(budgetsFile, BUDGET_LIST_TYPE, "budgets");
    }

    public void saveBudgets(List<Budget> budgets) throws IOException {
        writeList(budgetsFile, Objects.requireNonNull(budgets, "budgets"));
    }

    private <T> List<T> readList(Path file, Type listType, String dataName) throws IOException {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        String json = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (json.isEmpty() || "null".equals(json)) {
            return new ArrayList<>();
        }

        try {
            List<T> values = gson.fromJson(json, listType);
            return values == null ? new ArrayList<>() : values;
        } catch (RuntimeException exception) {
            throw new IOException("Could not read " + dataName + " from " + file
                    + ". Check that it contains a valid JSON array.", exception);
        }
    }

    private void writeList(Path destination, List<?> values) throws IOException {
        Files.createDirectories(dataDirectory);
        String json = gson.toJson(values) + System.lineSeparator();
        Path temporaryFile = Files.createTempFile(dataDirectory, destination.getFileName().toString(), ".tmp");

        try {
            Files.writeString(temporaryFile, json, StandardCharsets.UTF_8);
            try {
                // The temporary file is in the same folder, allowing an atomic replacement on most filesystems.
                Files.move(temporaryFile, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                // Some filesystems do not support atomic moves; still replace only after the temp write succeeds.
                Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // If writing or moving failed, remove the incomplete temporary file and keep the old data file.
            Files.deleteIfExists(temporaryFile);
        }
    }
}
