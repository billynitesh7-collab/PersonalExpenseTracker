package com.expense.storage;

import com.expense.model.Budget;
import com.expense.model.Transaction;

import java.io.IOException;
import java.util.List;

/** Storage boundary shared by the legacy JSON server and the Spring Boot database server. */
public interface FinanceDataStore {
    List<Transaction> readTransactions() throws IOException;
    void saveTransactions(List<Transaction> transactions) throws IOException;
    List<Budget> readBudgets() throws IOException;
    void saveBudgets(List<Budget> budgets) throws IOException;

    /** JSON storage ignores ownership for the legacy server; PostgreSQL overrides these scoped methods. */
    default List<Transaction> readTransactions(long userId) throws IOException { return readTransactions(); }
    default void saveTransactions(long userId, List<Transaction> transactions) throws IOException { saveTransactions(transactions); }
    default List<Budget> readBudgets(long userId) throws IOException { return readBudgets(); }
    default void saveBudgets(long userId, List<Budget> budgets) throws IOException { saveBudgets(budgets); }
}
