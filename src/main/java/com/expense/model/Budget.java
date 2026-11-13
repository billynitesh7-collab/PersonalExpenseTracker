package com.expense.model;

import java.math.BigDecimal;
import java.time.YearMonth;

/** A spending limit for one calendar month. */
public class Budget {
    private String id;
    private YearMonth month;
    private BigDecimal amount;

    /** Required by Gson when it creates an object while reading JSON. */
    public Budget() {
    }

    public Budget(String id, YearMonth month, BigDecimal amount) {
        this.id = id;
        this.month = month;
        this.amount = amount;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public YearMonth getMonth() { return month; }
    public void setMonth(YearMonth month) { this.month = month; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
