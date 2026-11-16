package com.expense.storage;

import com.expense.model.Budget;
import com.expense.model.Transaction;
import com.expense.model.TransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** PostgreSQL implementation used by Spring Boot; all financial operations require an owner ID. */
public class PostgresFinanceDataStore implements FinanceDataStore {
    private static final RowMapper<Transaction> TRANSACTION_MAPPER = (row, index) -> new Transaction(
            row.getString("id"), TransactionType.valueOf(row.getString("type")), row.getBigDecimal("amount"),
            row.getString("category"), row.getObject("date", LocalDate.class), row.getString("description"));
    private static final RowMapper<Budget> BUDGET_MAPPER = (row, index) -> new Budget(
            row.getString("id"), YearMonth.parse(row.getString("month").trim()), row.getBigDecimal("amount"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PostgresFinanceDataStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<Transaction> readTransactions() throws IOException {
        throw new IOException("An authenticated user is required to read transactions.");
    }

    @Override
    public List<Transaction> readTransactions(long userId) throws IOException {
        try {
            return jdbc.query("SELECT id, type, amount, category, date, description FROM transactions " +
                    "WHERE user_id = ? ORDER BY display_order, id", TRANSACTION_MAPPER, userId);
        } catch (RuntimeException exception) {
            throw storageError("read transactions", exception);
        }
    }

    /** Unscoped writes are rejected so callers cannot bypass ownership checks. */
    @Override
    public void saveTransactions(List<Transaction> values) throws IOException {
        throw new IOException("An authenticated user is required to write transactions.");
    }

    @Override
    public void saveTransactions(long userId, List<Transaction> values) throws IOException {
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM transactions WHERE user_id = ?", userId);
                for (int i = 0; i < values.size(); i++) {
                    Transaction value = values.get(i);
                    jdbc.update("INSERT INTO transactions " +
                                    "(id, user_id, type, amount, category, date, description, display_order) " +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            value.getId(), userId, value.getType().name(), value.getAmount(), value.getCategory(),
                            value.getDate(), value.getDescription(), i);
                }
            });
        } catch (RuntimeException exception) {
            throw storageError("save transactions", exception);
        }
    }

    @Override
    public List<Budget> readBudgets() throws IOException {
        throw new IOException("An authenticated user is required to read budgets.");
    }

    @Override
    public List<Budget> readBudgets(long userId) throws IOException {
        try {
            return jdbc.query("SELECT id, month, amount FROM budgets WHERE user_id = ? ORDER BY month", BUDGET_MAPPER, userId);
        } catch (RuntimeException exception) {
            throw storageError("read budgets", exception);
        }
    }

    /** Unscoped writes are rejected so callers cannot bypass ownership checks. */
    @Override
    public void saveBudgets(List<Budget> values) throws IOException {
        throw new IOException("An authenticated user is required to write budgets.");
    }

    @Override
    public void saveBudgets(long userId, List<Budget> values) throws IOException {
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM budgets WHERE user_id = ?", userId);
                for (Budget value : values) {
                    jdbc.update("INSERT INTO budgets (id, user_id, month, amount) VALUES (?, ?, ?, ?)",
                            value.getId(), userId, value.getMonth().toString(), value.getAmount());
                }
            });
        } catch (RuntimeException exception) {
            throw storageError("save budgets", exception);
        }
    }

    private static IOException storageError(String operation, RuntimeException cause) {
        // The API returns a generic server error; this message is for local server logs only.
        return new IOException("Could not " + operation + " in PostgreSQL.", cause);
    }
}
