package com.expense.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One income or expense entry. Money uses BigDecimal to avoid floating-point rounding. */
public class Transaction {
    private String id;
    private TransactionType type;
    private BigDecimal amount;
    private String category;
    private LocalDate date;
    private String description;

    /** Required by Gson when it creates an object while reading JSON. */
    public Transaction() {
    }

    public Transaction(String id, TransactionType type, BigDecimal amount,
                       String category, LocalDate date, String description) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.category = category;
        this.date = date;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
