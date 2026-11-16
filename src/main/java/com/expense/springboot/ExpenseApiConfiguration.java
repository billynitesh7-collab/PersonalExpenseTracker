package com.expense.springboot;

import com.expense.service.BudgetService;
import com.expense.service.ReportService;
import com.expense.service.TransactionService;
import com.expense.storage.FinanceDataStore;
import com.expense.storage.PostgresFinanceDataStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Connects the Spring application to the PostgreSQL-backed production store. */
@Configuration
public class ExpenseApiConfiguration {
    @Bean FinanceDataStore financeDataStore(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        return new PostgresFinanceDataStore(jdbcTemplate, transactionManager);
    }
    @Bean TransactionService transactionService(FinanceDataStore dataStore) { return new TransactionService(dataStore); }
    @Bean BudgetService budgetService(FinanceDataStore dataStore, TransactionService transactions) {
        return new BudgetService(dataStore, transactions);
    }
    @Bean ReportService reportService(TransactionService transactions, BudgetService budgets) {
        return new ReportService(transactions, budgets);
    }
}
