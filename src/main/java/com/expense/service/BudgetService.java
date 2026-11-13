package com.expense.service;

import com.expense.model.Budget;
import com.expense.model.BudgetSummary;
import com.expense.model.Transaction;
import com.expense.model.TransactionType;
import com.expense.storage.JsonDataStore;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Manages monthly budgets and calculates spending against each monthly limit. */
public class BudgetService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int PERCENTAGE_SCALE = 2;

    private final JsonDataStore dataStore;
    private final TransactionService transactionService;

    public BudgetService(JsonDataStore dataStore, TransactionService transactionService) {
        this.dataStore = dataStore;
        this.transactionService = transactionService;
    }

    /** Creates a budget for the month or updates the existing one without adding a duplicate. */
    public synchronized Budget createOrUpdateBudget(YearMonth month, BigDecimal amount) throws IOException {
        validate(month, amount);
        List<Budget> budgets = dataStore.readBudgets();
        Budget existing = budgets.stream()
                .filter(budget -> month.equals(budget.getMonth()))
                .findFirst()
                .orElse(null);

        String id = existing != null && existing.getId() != null && !existing.getId().isBlank()
                ? existing.getId()
                : "budget-" + month;
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

        dataStore.saveBudgets(updatedBudgets);
        return savedBudget;
    }

    public synchronized Optional<Budget> getBudgetForMonth(YearMonth month) throws IOException {
        validateMonth(month);
        return dataStore.readBudgets().stream()
                .filter(budget -> month.equals(budget.getMonth()))
                .findFirst();
    }

    public synchronized List<Budget> getAllBudgets() throws IOException {
        return dataStore.readBudgets();
    }

    public synchronized List<BudgetSummary> getAllBudgetSummaries() throws IOException {
        List<Budget> budgets = getAllBudgets();
        budgets.sort(Comparator.comparing(Budget::getMonth,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<BudgetSummary> summaries = new ArrayList<>();
        for (Budget budget : budgets) {
            if (budget.getMonth() != null) summaries.add(createSummary(budget.getMonth(), budget));
        }
        return summaries;
    }

    /** Returns a summary even when no budget exists; budget-dependent values are then absent. */
    public synchronized BudgetSummary getBudgetSummary(YearMonth month) throws IOException {
        validateMonth(month);
        Budget budget = getBudgetForMonth(month).orElse(null);
        return createSummary(month, budget);
    }

    public synchronized BigDecimal calculateTotalExpenses(YearMonth month) throws IOException {
        validateMonth(month);
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction transaction : transactionService.getAllTransactions()) {
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
        Optional<Budget> budget = getBudgetForMonth(month);
        if (budget.isEmpty()) return Optional.empty();
        return Optional.of(budget.get().getAmount().subtract(calculateTotalExpenses(month)));
    }

    public synchronized Optional<BigDecimal> calculateUsagePercentage(YearMonth month) throws IOException {
        Optional<Budget> budget = getBudgetForMonth(month);
        if (budget.isEmpty()) return Optional.empty();
        BigDecimal expenses = calculateTotalExpenses(month);
        return Optional.of(expenses.multiply(ONE_HUNDRED)
                .divide(budget.get().getAmount(), PERCENTAGE_SCALE, RoundingMode.HALF_UP));
    }

    public synchronized boolean isBudgetExceeded(YearMonth month) throws IOException {
        Optional<Budget> budget = getBudgetForMonth(month);
        return budget.isPresent()
                && calculateTotalExpenses(month).compareTo(budget.get().getAmount()) > 0;
    }

    private BudgetSummary createSummary(YearMonth month, Budget budget) throws IOException {
        BigDecimal expenses = calculateTotalExpenses(month);
        if (budget == null) {
            return new BudgetSummary(null, month, null, expenses, null, null, false);
        }

        BigDecimal remaining = budget.getAmount().subtract(expenses);
        BigDecimal percentage = expenses.multiply(ONE_HUNDRED)
                .divide(budget.getAmount(), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
        return new BudgetSummary(budget.getId(), month, budget.getAmount(), expenses,
                remaining, percentage, expenses.compareTo(budget.getAmount()) > 0);
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
