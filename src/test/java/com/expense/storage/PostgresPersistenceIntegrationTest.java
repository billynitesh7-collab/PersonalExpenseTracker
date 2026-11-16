package com.expense.storage;

import com.expense.model.Budget;
import com.expense.model.BudgetSummary;
import com.expense.model.Transaction;
import com.expense.model.TransactionType;
import com.expense.service.BudgetService;
import com.expense.service.ReportService;
import com.expense.service.TransactionService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uses only an explicitly configured or locally named test database and drops each synthetic schema. */
class PostgresPersistenceIntegrationTest {
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 11, 3);
    private static final YearMonth TEST_MONTH = YearMonth.of(2026, 11);

    @Test
    void migratesAndEnforcesTwoUserStorageIsolation() throws Exception {
        DbConnection db = connectionSettings();
        String schema = createSchema(db);
        try {
            newFlyway(db, schema).migrate();
            DriverManagerDataSource dataSource = createDataSource(db, schema);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            PostgresFinanceDataStore store = createStore(jdbc, dataSource);

            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
            long userA = createUser(jdbc, "alice-" + UUID.randomUUID() + "@example.test");
            long userB = createUser(jdbc, "blake-" + UUID.randomUUID() + "@example.test");

            TransactionService transactions = new TransactionService(store);
            BudgetService budgets = new BudgetService(store, transactions);
            ReportService reports = new ReportService(transactions, budgets);

            transactions.addTransaction(new Transaction("synthetic-income", TransactionType.INCOME,
                    new BigDecimal("125000.55"), "Salary", TEST_DATE, "synthetic salary"), userA);
            transactions.addTransaction(new Transaction("synthetic-food-1", TransactionType.EXPENSE,
                    new BigDecimal("0.10"), "Food", TEST_DATE, "synthetic snack"), userA);
            String foodId = transactions.addTransaction(new Transaction("synthetic-food-2", TransactionType.EXPENSE,
                    new BigDecimal("0.20"), "Food", TEST_DATE, "synthetic dinner"), userA).getId();
            String deletedId = transactions.addTransaction(new Transaction("synthetic-delete", TransactionType.EXPENSE,
                    new BigDecimal("500.00"), "Other", TEST_DATE, "synthetic delete check"), userA).getId();
            String privateId = transactions.addTransaction(new Transaction("client-choice", TransactionType.EXPENSE,
                    new BigDecimal("4.00"), "Travel", TEST_DATE, "synthetic private record"), userB).getId();
            assertFalse("client-choice".equals(privateId), "Scoped creates assign IDs on the server.");

            Transaction changed = new Transaction(foodId, TransactionType.EXPENSE,
                    new BigDecimal("0.30"), "Food", TEST_DATE, "synthetic dinner updated");
            transactions.updateTransaction(foodId, changed, userA);
            assertEquals(new BigDecimal("0.30"), transactions.findTransaction(foodId, userA).getAmount());
            assertEquals(1, transactions.searchTransactions(TransactionType.EXPENSE, "Food", TEST_DATE, TEST_DATE,
                    "updated", userA).size());
            transactions.deleteTransaction(deletedId, userA);
            assertThrows(java.util.NoSuchElementException.class, () -> transactions.findTransaction(deletedId, userA));

            assertEquals(3, transactions.getAllTransactions(userA).size());
            assertEquals(1, transactions.getAllTransactions(userB).size());
            assertThrows(java.util.NoSuchElementException.class, () -> transactions.findTransaction(privateId, userA));
            assertThrows(java.util.NoSuchElementException.class, () -> transactions.updateTransaction(privateId,
                    new Transaction(privateId, TransactionType.EXPENSE, BigDecimal.ONE, "Food", TEST_DATE, "probe"), userA));
            assertThrows(java.util.NoSuchElementException.class, () -> transactions.deleteTransaction(privateId, userA));

            budgets.createOrUpdateBudget(TEST_MONTH, new BigDecimal("0.50"), userA);
            budgets.createOrUpdateBudget(TEST_MONTH, new BigDecimal("0.25"), userA);
            budgets.createOrUpdateBudget(TEST_MONTH, new BigDecimal("10.00"), userB);
            BudgetSummary budgetA = budgets.getBudgetSummary(TEST_MONTH, userA);
            BudgetSummary budgetB = budgets.getBudgetSummary(TEST_MONTH, userB);
            assertEquals(new BigDecimal("0.40"), budgetA.getTotalExpenses());
            assertEquals(new BigDecimal("-0.15"), budgetA.getRemainingBudget());
            assertEquals(new BigDecimal("160.00"), budgetA.getUsagePercentage());
            assertTrue(budgetA.isExceeded());
            assertEquals(new BigDecimal("4.00"), budgetB.getTotalExpenses());
            assertEquals(new BigDecimal("6.00"), budgetB.getRemainingBudget());
            assertEquals(1, budgets.getAllBudgets(userA).size());
            assertEquals(1, budgets.getAllBudgets(userB).size());

            var summaryA = reports.getFinancialSummary(userA);
            var summaryB = reports.getFinancialSummary(userB);
            assertEquals(new BigDecimal("125000.55"), summaryA.totalIncome());
            assertEquals(new BigDecimal("0.40"), summaryA.totalExpenses());
            assertEquals(new BigDecimal("125000.15"), summaryA.netSavings());
            assertEquals(3, summaryA.totalTransactions());
            assertEquals(BigDecimal.ZERO, summaryB.totalIncome());
            assertEquals(new BigDecimal("4.00"), summaryB.totalExpenses());
            assertEquals(new BigDecimal("-4.00"), summaryB.netSavings());
            assertEquals(new BigDecimal("0.40"), reports.getDailyReport(TEST_DATE, userA).dailyExpenses());
            assertEquals(new BigDecimal("4.00"), reports.getDailyReport(TEST_DATE, userB).dailyExpenses());
            assertEquals(new BigDecimal("0.40"), reports.getMonthlyReport(TEST_MONTH, userA).monthlyExpenses());
            assertEquals(new BigDecimal("4.00"), reports.getYearlyReport(Year.of(2026), userB).yearlyExpenses());
            assertEquals(new BigDecimal("0.40"), reports.getCategoryReport(TEST_MONTH, userA).totalExpenses());
            assertEquals("Food", reports.getCategoryReport(TEST_MONTH, userA).highestSpendingCategory());
            assertEquals("Travel", reports.getCategoryReport(TEST_MONTH, userB).highestSpendingCategory());

            verifyDatabaseConstraints(jdbc);

            PostgresFinanceDataStore restartedStore = createStore(new JdbcTemplate(createDataSource(db, schema)),
                    createDataSource(db, schema));
            assertEquals(new BigDecimal("125000.55"), restartedStore.readTransactions(userA).getFirst().getAmount());
            assertEquals(new BigDecimal("0.25"), restartedStore.readBudgets(userA).getFirst().getAmount());
        } finally {
            dropSchema(db, schema);
        }
    }

    @Test
    void ownershipMigrationRefusesToAssignUnownedLegacyRows() throws Exception {
        DbConnection db = connectionSettings();
        String schema = createSchema(db);
        try {
            Flyway.configure().dataSource(db.url(), db.username(), db.password())
                    .schemas(schema).defaultSchema(schema).createSchemas(false)
                    .target(MigrationVersion.fromVersion("1")).load().migrate();
            DriverManagerDataSource dataSource = createDataSource(db, schema);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("INSERT INTO transactions (id, type, amount, category, date, description) " +
                    "VALUES ('synthetic-unowned', 'EXPENSE', 1, 'Food', ?, 'synthetic legacy row')", TEST_DATE);

            assertThrows(FlywayException.class, () -> newFlyway(db, schema).migrate());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM transactions WHERE user_id IS NULL", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history " +
                    "WHERE version = '2' AND success", Integer.class));
        } finally {
            dropSchema(db, schema);
        }
    }

    private static void verifyDatabaseConstraints(JdbcTemplate jdbc) {
        long owner = createUser(jdbc, "constraint-" + UUID.randomUUID() + "@example.test");
        jdbc.update("INSERT INTO users (email, password_hash) VALUES (?, ?)", "duplicate@example.test", "first-hash");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO users (email, password_hash) VALUES (?, ?)", "duplicate@example.test", "second-hash"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO transactions " +
                        "(id, user_id, type, amount, category, date, description) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "synthetic-bad-fk", -1L, "EXPENSE", BigDecimal.ONE, "Food", TEST_DATE, "bad FK"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO transactions " +
                        "(id, user_id, type, amount, category, date, description) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "synthetic-bad-type", owner, "OTHER", BigDecimal.ONE, "Food", TEST_DATE, "bad type"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO transactions " +
                        "(id, user_id, type, amount, category, date, description) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "synthetic-bad-amount", owner, "EXPENSE", BigDecimal.ZERO, "Food", TEST_DATE, "bad amount"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO transactions " +
                        "(id, user_id, type, amount, category, date, description) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "synthetic-bad-category", owner, "EXPENSE", BigDecimal.ONE, "  ", TEST_DATE, "bad category"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO budgets (id, user_id, month, amount) VALUES (?, ?, ?, ?)",
                "synthetic-bad-month", owner, "2026-13", BigDecimal.ONE));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO budgets (id, user_id, month, amount) VALUES (?, ?, ?, ?)",
                "synthetic-bad-budget-fk", -1L, "2026-12", BigDecimal.ONE));
        jdbc.update("INSERT INTO budgets (id, user_id, month, amount) VALUES (?, ?, ?, ?)",
                "synthetic-user-budget", owner, "2026-12", BigDecimal.ONE);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO budgets (id, user_id, month, amount) VALUES (?, ?, ?, ?)",
                "synthetic-duplicate-user-budget", owner, "2026-12", BigDecimal.TEN));
    }

    private static long createUser(JdbcTemplate jdbc, String email) {
        return jdbc.queryForObject("INSERT INTO users (email, password_hash) VALUES (?, ?) RETURNING id",
                Long.class, email, "synthetic-password-hash");
    }

    private static DbConnection connectionSettings() {
        String url = System.getenv().getOrDefault("POSTGRES_TEST_URL", "jdbc:postgresql://localhost:5432/expense_tracker_test");
        String username = System.getenv().getOrDefault("POSTGRES_TEST_USERNAME", "expense_tracker_test");
        String password = System.getenv().getOrDefault("POSTGRES_TEST_PASSWORD", "");
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            return new DbConnection(url, username, password);
        } catch (SQLException exception) {
            if (isConnectionUnavailable(exception)) {
                Assumptions.abort("PostgreSQL integration not run: the configured test database is unreachable.");
            }
            throw new IllegalStateException("Could not connect to the configured PostgreSQL test database.", exception);
        }
    }

    private static String createSchema(DbConnection db) throws SQLException {
        String schema = "pet_test_" + Long.toUnsignedString(System.nanoTime());
        try (var connection = DriverManager.getConnection(db.url(), db.username(), db.password());
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        return schema;
    }

    private static void dropSchema(DbConnection db, String schema) throws SQLException {
        try (var connection = DriverManager.getConnection(db.url(), db.username(), db.password());
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private static Flyway newFlyway(DbConnection db, String schema) {
        return Flyway.configure().dataSource(db.url(), db.username(), db.password())
                .schemas(schema).defaultSchema(schema).createSchemas(false).load();
    }

    private static DriverManagerDataSource createDataSource(DbConnection db, String schema) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(db.url() + (db.url().contains("?") ? "&" : "?") + "currentSchema=" + schema);
        dataSource.setUsername(db.username());
        dataSource.setPassword(db.password());
        return dataSource;
    }

    private static PostgresFinanceDataStore createStore(JdbcTemplate jdbc, DriverManagerDataSource dataSource) {
        return new PostgresFinanceDataStore(jdbc, new DataSourceTransactionManager(dataSource));
    }

    private static boolean isConnectionUnavailable(SQLException exception) {
        if (exception.getSQLState() != null && exception.getSQLState().startsWith("08")) return true;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof UnknownHostException) return true;
        }
        return false;
    }

    private record DbConnection(String url, String username, String password) { }
}
