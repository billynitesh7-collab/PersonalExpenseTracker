package com.expense.storage;

import com.expense.model.Budget;
import com.expense.model.Transaction;
import com.expense.model.TransactionType;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/** Small runnable check for the file store. It uses a temporary folder, not the app's real data. */
public class JsonDataStoreTest {
    public static void main(String[] args) throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("expense-storage-test-");
        try {
            verifyStorage(temporaryDirectory);
            System.out.println("JsonDataStore checks passed.");
        } finally {
            deleteTemporaryDirectory(temporaryDirectory);
        }
    }

    private static void verifyStorage(Path temporaryDirectory) throws Exception {
        Path dataDirectory = temporaryDirectory.resolve("data");
        JsonDataStore store = new JsonDataStore(dataDirectory);

        // Reading before initialization and reading a truly blank file both return empty lists.
        check(store.readTransactions().isEmpty(), "A missing transaction file should read as empty.");
        DataInitializer.initialize(dataDirectory);
        check(Files.exists(dataDirectory.resolve("transactions.json")), "Transaction file was not created.");
        check(Files.exists(dataDirectory.resolve("budgets.json")), "Budget file was not created.");
        check(store.readTransactions().isEmpty(), "A new transaction file should be empty.");
        check(store.readBudgets().isEmpty(), "A new budget file should be empty.");
        Files.writeString(dataDirectory.resolve("transactions.json"), "   ");
        check(store.readTransactions().isEmpty(), "A blank transaction file should read as empty.");

        BigDecimal preciseTransactionAmount = new BigDecimal("123456789.12345678901234567890");
        Transaction transaction = new Transaction(
                "txn-test-1", TransactionType.EXPENSE, preciseTransactionAmount,
                "Education", LocalDate.of(2026, 11, 6), "Precision test");
        store.saveTransactions(List.of(transaction));

        List<Transaction> loadedTransactions = store.readTransactions();
        check(loadedTransactions.size() == 1, "The saved transaction was not loaded.");
        check(loadedTransactions.get(0).getAmount().equals(preciseTransactionAmount),
                "The transaction amount lost BigDecimal precision.");
        check(loadedTransactions.get(0).getDate().equals(LocalDate.of(2026, 11, 6)),
                "The transaction date did not round-trip.");

        BigDecimal preciseBudgetAmount = new BigDecimal("98765.43210987654321");
        Budget budget = new Budget("budget-2026-11", YearMonth.of(2026, 11), preciseBudgetAmount);
        store.saveBudgets(List.of(budget));
        List<Budget> loadedBudgets = store.readBudgets();
        check(loadedBudgets.size() == 1, "The saved budget was not loaded.");
        check(loadedBudgets.get(0).getAmount().equals(preciseBudgetAmount),
                "The budget amount lost BigDecimal precision.");
        check(loadedBudgets.get(0).getMonth().equals(YearMonth.of(2026, 11)),
                "The budget month did not round-trip.");

        // Initializing again must leave both files byte-for-byte unchanged.
        String transactionsBeforeInitialization = Files.readString(dataDirectory.resolve("transactions.json"));
        String budgetsBeforeInitialization = Files.readString(dataDirectory.resolve("budgets.json"));
        DataInitializer.initialize(dataDirectory);
        check(Files.readString(dataDirectory.resolve("transactions.json")).equals(transactionsBeforeInitialization),
                "Initialization overwrote existing transactions.");
        check(Files.readString(dataDirectory.resolve("budgets.json")).equals(budgetsBeforeInitialization),
                "Initialization overwrote existing budgets.");

        Files.writeString(dataDirectory.resolve("transactions.json"), "{ invalid json }");
        try {
            store.readTransactions();
            throw new AssertionError("Invalid JSON should report a clear error.");
        } catch (IOException expected) {
            check(expected.getMessage().contains("valid JSON array")
                            && expected.getMessage().contains("transactions.json"),
                    "The invalid JSON error should name the problem and file.");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void deleteTemporaryDirectory(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
