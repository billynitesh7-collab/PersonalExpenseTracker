package com.expense.service;

import com.expense.model.Transaction;
import com.expense.model.TransactionType;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds read-only financial summaries from the saved transaction data. */
public class ReportService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int PERCENTAGE_SCALE = 2;

    private final TransactionService transactionService;
    private final BudgetService budgetService;

    public ReportService(TransactionService transactionService, BudgetService budgetService) {
        this.transactionService = transactionService;
        this.budgetService = budgetService;
    }

    public FinancialSummary getFinancialSummary() throws IOException {
        List<Transaction> transactions = transactionService.getAllTransactions();
        Totals totals = calculateTotals(transactions);
        return new FinancialSummary(totals.income, totals.expenses,
                totals.income.subtract(totals.expenses), transactions.size());
    }

    public DailyReport getDailyReport(LocalDate date) throws IOException {
        requireDate(date);
        List<Transaction> matches = transactionService.getAllTransactions().stream()
                .filter(transaction -> date.equals(transaction.getDate()))
                .toList();
        Totals totals = calculateTotals(matches);
        return new DailyReport(date, totals.income, totals.expenses,
                totals.income.subtract(totals.expenses), matches.size());
    }

    public MonthlyReport getMonthlyReport(YearMonth month) throws IOException {
        requireMonth(month);
        List<Transaction> transactions = transactionService.getAllTransactions();
        MonthTotals selected = monthTotals(month, transactions);
        List<MonthTotals> comparison = monthlyBreakdown(month.getYear(), transactions);
        return new MonthlyReport(selected.month, selected.monthlyIncome, selected.monthlyExpenses,
                selected.monthlySavings, selected.transactionCount, comparison);
    }

    public YearlyReport getYearlyReport(Year year) throws IOException {
        requireYear(year);
        List<Transaction> transactions = transactionService.getAllTransactions();
        List<MonthTotals> breakdown = monthlyBreakdown(year.getValue(), transactions);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        long count = 0;
        for (MonthTotals month : breakdown) {
            income = income.add(month.monthlyIncome);
            expenses = expenses.add(month.monthlyExpenses);
            count += month.transactionCount;
        }
        return new YearlyReport(year.getValue(), income, expenses, income.subtract(expenses), count, breakdown);
    }

    public CategoryReport getCategoryReport(YearMonth month) throws IOException {
        requireMonth(month);
        List<Transaction> transactions = transactionService.getAllTransactions().stream()
                .filter(transaction -> transaction.getType() == TransactionType.EXPENSE)
                .filter(transaction -> transaction.getDate() != null
                        && month.equals(YearMonth.from(transaction.getDate())))
                .toList();

        // The budget service uses the same transaction source and performs the authoritative monthly expense sum.
        BigDecimal totalExpenses = budgetService.calculateTotalExpenses(month);
        Map<String, BigDecimal> categoryAmounts = new TreeMap<>();
        for (Transaction transaction : transactions) {
            if (transaction.getCategory() == null || transaction.getAmount() == null) continue;
            categoryAmounts.merge(transaction.getCategory(), transaction.getAmount(), BigDecimal::add);
        }

        List<CategoryExpense> categories = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : categoryAmounts.entrySet()) {
            BigDecimal percentage = totalExpenses.signum() == 0 ? BigDecimal.ZERO
                    : entry.getValue().multiply(ONE_HUNDRED)
                    .divide(totalExpenses, PERCENTAGE_SCALE, RoundingMode.HALF_UP);
            categories.add(new CategoryExpense(entry.getKey(), entry.getValue(), percentage));
        }
        categories.sort(Comparator.comparing(CategoryExpense::totalExpenses).reversed()
                .thenComparing(CategoryExpense::category));

        String highest = categories.isEmpty() ? null : categories.get(0).category;
        String lowest = categories.isEmpty() ? null : categories.get(categories.size() - 1).category;
        return new CategoryReport(month, totalExpenses, categories, highest, lowest);
    }

    private List<MonthTotals> monthlyBreakdown(int year, List<Transaction> transactions) {
        List<MonthTotals> result = new ArrayList<>();
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            YearMonth month = YearMonth.of(year, monthNumber);
            Totals totals = calculateTotalsForMonth(month, transactions);
            result.add(new MonthTotals(month, totals.income, totals.expenses,
                    totals.income.subtract(totals.expenses), totals.transactionCount));
        }
        return result;
    }

    private static MonthTotals monthTotals(YearMonth month, List<Transaction> transactions) {
        Totals totals = calculateTotalsForMonth(month, transactions);
        return new MonthTotals(month, totals.income, totals.expenses,
                totals.income.subtract(totals.expenses), totals.transactionCount);
    }

    private static Totals calculateTotalsForMonth(YearMonth month, List<Transaction> transactions) {
        return calculateTotals(transactions.stream()
                .filter(transaction -> transaction.getDate() != null
                        && month.equals(YearMonth.from(transaction.getDate())))
                .toList());
    }

    private static Totals calculateTotals(List<Transaction> transactions) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        for (Transaction transaction : transactions) {
            if (transaction.getAmount() == null || transaction.getType() == null) continue;
            if (transaction.getType() == TransactionType.INCOME) income = income.add(transaction.getAmount());
            if (transaction.getType() == TransactionType.EXPENSE) expenses = expenses.add(transaction.getAmount());
        }
        return new Totals(income, expenses, transactions.size());
    }

    private static void requireDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("A valid report date is required.");
    }

    private static void requireMonth(YearMonth month) {
        if (month == null) throw new IllegalArgumentException("A valid report month is required.");
    }

    private static void requireYear(Year year) {
        if (year == null) throw new IllegalArgumentException("A valid report year is required.");
    }

    private record Totals(BigDecimal income, BigDecimal expenses, long transactionCount) { }

    public record FinancialSummary(BigDecimal totalIncome, BigDecimal totalExpenses,
                                   BigDecimal netSavings, long totalTransactions) { }

    public record DailyReport(LocalDate date, BigDecimal dailyIncome, BigDecimal dailyExpenses,
                              BigDecimal dailySavings, long transactionCount) { }

    public record MonthTotals(YearMonth month, BigDecimal monthlyIncome, BigDecimal monthlyExpenses,
                              BigDecimal monthlySavings, long transactionCount) { }

    public record MonthlyReport(YearMonth month, BigDecimal monthlyIncome, BigDecimal monthlyExpenses,
                                BigDecimal monthlySavings, long transactionCount,
                                List<MonthTotals> monthlyComparison) { }

    public record YearlyReport(int year, BigDecimal yearlyIncome, BigDecimal yearlyExpenses,
                               BigDecimal yearlySavings, long transactionCount,
                               List<MonthTotals> monthlyBreakdown) { }

    public record CategoryExpense(String category, BigDecimal totalExpenses, BigDecimal percentage) { }

    public record CategoryReport(YearMonth month, BigDecimal totalExpenses,
                                 List<CategoryExpense> categories, String highestSpendingCategory,
                                 String lowestSpendingCategory) { }
}
