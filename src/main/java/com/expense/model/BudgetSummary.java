package com.expense.model;

import java.math.BigDecimal;
import java.time.YearMonth;

/** A monthly budget together with its spending calculations for API responses. */
public class BudgetSummary {
    private final String id;
    private final YearMonth month;
    private final BigDecimal budgetAmount;
    private final BigDecimal totalExpenses;
    private final BigDecimal remainingBudget;
    private final BigDecimal usagePercentage;
    private final boolean exceeded;

    public BudgetSummary(String id, YearMonth month, BigDecimal budgetAmount,
                         BigDecimal totalExpenses, BigDecimal remainingBudget,
                         BigDecimal usagePercentage, boolean exceeded) {
        this.id = id;
        this.month = month;
        this.budgetAmount = budgetAmount;
        this.totalExpenses = totalExpenses;
        this.remainingBudget = remainingBudget;
        this.usagePercentage = usagePercentage;
        this.exceeded = exceeded;
    }

    public String getId() { return id; }
    public YearMonth getMonth() { return month; }
    public BigDecimal getBudgetAmount() { return budgetAmount; }
    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public BigDecimal getRemainingBudget() { return remainingBudget; }
    public BigDecimal getUsagePercentage() { return usagePercentage; }
    public boolean isExceeded() { return exceeded; }
}
