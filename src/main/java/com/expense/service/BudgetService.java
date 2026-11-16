package com.expense.service;

import com.expense.model.Budget;
import com.expense.model.BudgetSummary;
import com.expense.model.Transaction;
import com.expense.model.TransactionType;
import com.expense.storage.FinanceDataStore;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Manages monthly budgets and calculates spending against each monthly limit. */
public class BudgetService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int PERCENTAGE_SCALE = 2;

    private final FinanceDataStore dataStore;
    private final TransactionService transactionService;

    public BudgetService(FinanceDataStore dataStore, TransactionService transactionService) {
        this.dataStore = dataStore;
        this.transactionService = transactionService;
    }

    /** Creates a budget for the month or updates the existing one without adding a duplicate. */
    public synchronized Budget createOrUpdateBudget(YearMonth month, BigDecimal amount) throws IOException {
        return createOrUpdateBudget(month, amount, null);
    }

    public synchronized Budget createOrUpdateBudget(YearMonth month, BigDecimal amount, long userId) throws IOException {
        return createOrUpdateBudget(month, amount, Long.valueOf(userId));
    }

    private Budget createOrUpdateBudget(YearMonth month, BigDecimal amount, Long userId) throws IOException {
        validate(month, amount);
        List<Budget> budgets = readBudgets(userId);
        Budget existing = budgets.stream()
                .filter(budget -> month.equals(budget.getMonth()))
                .findFirst()
                .orElse(null);

        String id = existing != null && existing.getId() != null && !existing.getId().isBlank()
                ? existing.getId()
                : userId == null ? "budget-" + month : UUID.randomUUID().toString();
        Budget savedBudget = new Budget(id, month, amount);

        // Preserve other months and collapse any old duplicates for this month to one record.
        List<Budget> updatedBudgets = new ArrayList<>();
        boolean inserted = false;
        for (Budget budget : budgets) {
            if (month.equals(budget.getMonth())) {
                if (!inserted) {
                    updatedBudgets.add(savedBudget);
                    inserted = true;
                }
            } else {
                updatedBudgets.add(budget);
            }
        }
        if (!inserted) updatedBudgets.add(savedBudget);

        saveBudgets(userId, updatedBudgets);
        return savedBudget;
    }

    public synchronized Optional<Budget> getBudgetForMonth(YearMonth month) throws IOException {
        return getBudgetForMonth(month, null);
    }

    public synchronized Optional<Budget> getBudgetForMonth(YearMonth month, long userId) throws IOException {
        return getBudgetForMonth(month, Long.valueOf(userId));
    }

    private Optional<Budget> getBudgetForMonth(YearMonth month, Long userId) throws IOException {
        validateMonth(month);
        return readBudgets(userId).stream()
                .filter(budget -> month.equals(budget.getMonth()))
                .findFirst();
    }

    public synchronized List<Budget> getAllBudgets() throws IOException {
        return readBudgets(null);
    }

    public synchronized List<Budget> getAllBudgets(long userId) throws IOException { return readBudgets(userId); }

    public synchronized List<BudgetSummary> getAllBudgetSummaries() throws IOException {
        List<Budget> budgets = getAllBudgets();
        return summarizeBudgets(budgets, null);
    }

    public synchronized List<BudgetSummary> getAllBudgetSummaries(long userId) throws IOException {
        return summarizeBudgets(getAllBudgets(userId), Long.valueOf(userId));
    }

    private List<BudgetSummary> summarizeBudgets(List<Budget> budgets, Long userId) throws IOException {
        budgets.sort(Comparator.comparing(Budget::getMonth,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<BudgetSummary> summaries = new ArrayList<>();
        for (Budget budget : budgets) {
            if (budget.getMonth() != null) summaries.add(createSummary(budget.getMonth(), budget, userId));
        }
        return summaries;
    }

    /** Returns a summary even when no budget exists; budget-dependent values are then absent. */
    public synchronized BudgetSummary getBudgetSummary(YearMonth month) throws IOException {
        return getBudgetSummary(month, null);
    }

    public synchronized BudgetSummary getBudgetSummary(YearMonth month, long userId) throws IOException {
        return getBudgetSummary(month, Long.valueOf(userId));
    }

    private BudgetSummary getBudgetSummary(YearMonth month, Long userId) throws IOException {
        validateMonth(month);
        Budget budget = getBudgetForMonth(month, userId).orElse(null);
        return createSummary(month, budget, userId);
    }

    public synchronized BigDecimal calculateTotalExpenses(YearMonth month) throws IOException {
        return calculateTotalExpenses(month, null);
    }

    public synchronized BigDecimal calculateTotalExpenses(YearMonth month, long userId) throws IOException {
        return calculateTotalExpenses(month, Long.valueOf(userId));
    }

    private BigDecimal calculateTotalExpenses(YearMonth month, Long userId) throws IOException {
        validateMonth(month);
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction transaction : userId == null ? transactionService.getAllTransactions() : transactionService.getAllTransactions(userId)) {
            if (transaction.getType() == TransactionType.EXPENSE
                    && transaction.getDate() != null
                    && month.equals(YearMonth.from(transaction.getDate()))
                    && transaction.getAmount() != null) {
                total = total.add(transaction.getAmount());
            }
        }
        return total;
    }

    public synchronized Optional<BigDecimal> calculateRemainingBudget(YearMonth month) throws IOException {
        return calculateRemainingBudget(month, null);
    }

    public synchronized Optional<BigDecimal> calculateRemainingBudget(YearMonth month, long userId) throws IOException {
        return calculateRemainingBudget(month, Long.valueOf(userId));
    }

    private Optional<BigDecimal> calculateRemainingBudget(YearMonth month, Long userId) throws IOException {
        Optional<Budget> budget = getBudgetForMonth(month, userId);
        if (budget.isEmpty()) return Optional.empty();
        return Optional.of(budget.get().getAmount().subtract(calculateTotalExpenses(month, userId)));
    }

    public synchronized Optional<BigDecimal> calculateUsagePercentage(YearMonth month) throws IOException {
        return calculateUsagePercentage(month, null);
    }

    public synchronized Optional<BigDecimal> calculateUsagePercentage(YearMonth month, long userId) throws IOException {
        return calculateUsagePercentage(month, Long.valueOf(userId));
    }

    private Optional<BigDecimal> calculateUsagePercentage(YearMonth month, Long userId) throws IOException {
        Optional<Budget> budget = getBudgetForMonth(month, userId);
        if (budget.isEmpty()) return Optional.empty();
        BigDecimal expenses = calculateTotalExpenses(month, userId);
        return Optional.of(expenses.multiply(ONE_HUNDRED)
                .divide(budget.get().getAmount(), PERCENTAGE_SCALE, RoundingMode.HALF_UP));
    }

    public synchronized boolean isBudgetExceeded(YearMonth month) throws IOException {
        return isBudgetExceeded(month, null);
    }

    public synchronized boolean isBudgetExceeded(YearMonth month, long userId) throws IOException {
        return isBudgetExceeded(month, Long.valueOf(userId));
    }

    private boolean isBudgetExceeded(YearMonth month, Long userId) throws IOException {
        Optional<Budget> budget = getBudgetForMonth(month, userId);
        return budget.isPresent()
                && calculateTotalExpenses(month, userId).compareTo(budget.get().getAmount()) > 0;
    }

    private BudgetSummary createSummary(YearMonth month, Budget budget, Long userId) throws IOException {
        BigDecimal expenses = calculateTotalExpenses(month, userId);
        if (budget == null) {
            return new BudgetSummary(null, month, null, expenses, null, null, false);
        }

        BigDecimal remaining = budget.getAmount().subtract(expenses);
        BigDecimal percentage = expenses.multiply(ONE_HUNDRED)
                .divide(budget.getAmount(), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
        return new BudgetSummary(budget.getId(), month, budget.getAmount(), expenses,
                remaining, percentage, expenses.compareTo(budget.getAmount()) > 0);
    }

    private List<Budget> readBudgets(Long userId) throws IOException {
        return userId == null ? dataStore.readBudgets() : dataStore.readBudgets(userId);
    }

    private void saveBudgets(Long userId, List<Budget> values) throws IOException {
        if (userId == null) dataStore.saveBudgets(values);
        else dataStore.saveBudgets(userId, values);
    }

    private static void validate(YearMonth month, BigDecimal amount) {
        validateMonth(month);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Budget amount must be greater than zero.");
        }
    }

    private static void validateMonth(YearMonth month) {
        if (month == null) throw new IllegalArgumentException("A valid budget month is required.");
    }
}
