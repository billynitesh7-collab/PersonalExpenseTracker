package com.expense.service;

import com.expense.model.Transaction;
import com.expense.model.TransactionType;
import com.expense.storage.FinanceDataStore;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Applies transaction rules and serializes read-modify-write operations. */
public class TransactionService {
    private static final int MAX_CATEGORY_LENGTH = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    private final FinanceDataStore dataStore;

    public TransactionService(FinanceDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public synchronized List<Transaction> getAllTransactions() throws IOException {
        return readTransactions(null);
    }

    public synchronized List<Transaction> getAllTransactions(long userId) throws IOException { return readTransactions(userId); }

    public synchronized List<Transaction> searchTransactions(TransactionType type, String category,
                                                              LocalDate startDate, LocalDate endDate,
                                                              String search) throws IOException {
        return searchTransactions(type, category, startDate, endDate, search, null);
    }

    public synchronized List<Transaction> searchTransactions(TransactionType type, String category,
                                                              LocalDate startDate, LocalDate endDate,
                                                              String search, long userId) throws IOException {
        return searchTransactions(type, category, startDate, endDate, search, Long.valueOf(userId));
    }

    private List<Transaction> searchTransactions(TransactionType type, String category, LocalDate startDate,
                                                  LocalDate endDate, String search, Long userId) throws IOException {
        List<Transaction> matches = new ArrayList<>();
        String normalizedCategory = category == null ? null : category.toLowerCase(Locale.ROOT);
        String normalizedSearch = search == null ? null : search.trim().toLowerCase(Locale.ROOT);

        for (Transaction transaction : readTransactions(userId)) {
            if (type != null && transaction.getType() != type) continue;
            if (normalizedCategory != null
                    && !transaction.getCategory().toLowerCase(Locale.ROOT).equals(normalizedCategory)) continue;
            if (startDate != null && transaction.getDate().isBefore(startDate)) continue;
            if (endDate != null && transaction.getDate().isAfter(endDate)) continue;
            if (normalizedSearch != null && !normalizedSearch.isEmpty()) {
                String searchableText = (transaction.getDescription() + " " + transaction.getCategory())
                        .toLowerCase(Locale.ROOT);
                if (!searchableText.contains(normalizedSearch)) continue;
            }
            matches.add(transaction);
        }
        return matches;
    }

    public synchronized Transaction findTransaction(String id) throws IOException {
        return findTransaction(id, null);
    }

    public synchronized Transaction findTransaction(String id, long userId) throws IOException { return findTransaction(id, Long.valueOf(userId)); }

    private Transaction findTransaction(String id, Long userId) throws IOException {
        return findIn(readTransactions(userId), id);
    }

    public synchronized Transaction addTransaction(Transaction transaction) throws IOException {
        return addTransaction(transaction, null);
    }

    public synchronized Transaction addTransaction(Transaction transaction, long userId) throws IOException {
        return addTransaction(transaction, Long.valueOf(userId));
    }

    private Transaction addTransaction(Transaction transaction, Long userId) throws IOException {
        validate(transaction);
        List<Transaction> transactions = readTransactions(userId);

        if (userId != null) {
            // Client-supplied IDs could reveal a collision with another user's globally unique ID.
            transaction.setId(createUniqueId(transactions));
        } else if (transaction.getId() == null || transaction.getId().isBlank()) {
            transaction.setId(createUniqueId(transactions));
        } else if (containsId(transactions, transaction.getId())) {
            throw new IllegalArgumentException("Transaction ID must be unique.");
        }

        transactions.add(transaction);
        saveTransactions(userId, transactions);
        return transaction;
    }

    public synchronized Transaction updateTransaction(String id, Transaction updated) throws IOException {
        return updateTransaction(id, updated, null);
    }

    public synchronized Transaction updateTransaction(String id, Transaction updated, long userId) throws IOException {
        return updateTransaction(id, updated, Long.valueOf(userId));
    }

    private Transaction updateTransaction(String id, Transaction updated, Long userId) throws IOException {
        validateId(id);
        validate(updated);
        if (updated.getId() != null && !updated.getId().isBlank() && !id.equals(updated.getId())) {
            throw new IllegalArgumentException("The transaction ID in the URL must match the request body.");
        }

        List<Transaction> transactions = readTransactions(userId);
        for (int index = 0; index < transactions.size(); index++) {
            if (id.equals(transactions.get(index).getId())) {
                updated.setId(id);
                transactions.set(index, updated);
                saveTransactions(userId, transactions);
                return updated;
            }
        }
        throw new NoSuchElementException("Transaction not found.");
    }

    public synchronized void deleteTransaction(String id) throws IOException {
        deleteTransaction(id, null);
    }

    public synchronized void deleteTransaction(String id, long userId) throws IOException {
        deleteTransaction(id, Long.valueOf(userId));
    }

    private void deleteTransaction(String id, Long userId) throws IOException {
        validateId(id);
        List<Transaction> transactions = readTransactions(userId);
        boolean removed = transactions.removeIf(transaction -> id.equals(transaction.getId()));
        if (!removed) {
            throw new NoSuchElementException("Transaction not found.");
        }
        saveTransactions(userId, transactions);
    }

    private List<Transaction> readTransactions(Long userId) throws IOException {
        return userId == null ? dataStore.readTransactions() : dataStore.readTransactions(userId);
    }

    private void saveTransactions(Long userId, List<Transaction> values) throws IOException {
        if (userId == null) dataStore.saveTransactions(values);
        else dataStore.saveTransactions(userId, values);
    }

    private static void validate(Transaction transaction) {
        if (transaction == null) throw new IllegalArgumentException("A transaction object is required.");
        if (transaction.getType() == null) throw new IllegalArgumentException("Type must be INCOME or EXPENSE.");
        BigDecimal amount = transaction.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (transaction.getCategory() == null || transaction.getCategory().isBlank()) {
            throw new IllegalArgumentException("Category cannot be empty.");
        }
        String category = transaction.getCategory().trim();
        if (category.length() > MAX_CATEGORY_LENGTH) {
            throw new IllegalArgumentException("Category cannot exceed " + MAX_CATEGORY_LENGTH + " characters.");
        }
        transaction.setCategory(category);
        if (transaction.getDate() == null) throw new IllegalArgumentException("Date must use YYYY-MM-DD format.");
        if (transaction.getDescription() == null || transaction.getDescription().isBlank()) {
            throw new IllegalArgumentException("Description cannot be empty.");
        }
        String description = transaction.getDescription().trim();
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description cannot exceed " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        transaction.setDescription(description);
    }

    private static void validateId(String id) {
        if (id == null || id.isBlank() || id.contains("/")) {
            throw new IllegalArgumentException("A valid transaction ID is required.");
        }
    }

    private static Transaction findIn(List<Transaction> transactions, String id) {
        validateId(id);
        return transactions.stream().filter(item -> id.equals(item.getId())).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Transaction not found."));
    }

    private static boolean containsId(List<Transaction> transactions, String id) {
        return transactions.stream().anyMatch(transaction -> id.equals(transaction.getId()));
    }

    private static String createUniqueId(List<Transaction> transactions) {
        String id;
        do {
            id = UUID.randomUUID().toString();
        } while (containsId(transactions, id));
        return id;
    }
}
