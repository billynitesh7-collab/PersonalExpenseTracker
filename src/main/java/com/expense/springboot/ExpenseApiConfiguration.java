package com.expense.springboot;

import com.expense.service.BudgetService;
import com.expense.service.ReportService;
import com.expense.service.TransactionService;
import com.expense.storage.DataInitializer;
import com.expense.storage.JsonDataStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.nio.file.Path;

/** Connects Spring controllers to the existing JSON-backed services. */
@Configuration
public class ExpenseApiConfiguration {
    @Bean JsonDataStore jsonDataStore(@Value("${expense.data-directory:data}") String directory) throws IOException {
        Path dataDirectory = Path.of(directory);
        DataInitializer.initialize(dataDirectory);
        return new JsonDataStore(dataDirectory);
    }
    @Bean TransactionService transactionService(JsonDataStore dataStore) { return new TransactionService(dataStore); }
    @Bean BudgetService budgetService(JsonDataStore dataStore, TransactionService transactions) {
        return new BudgetService(dataStore, transactions);
    }
    @Bean ReportService reportService(TransactionService transactions, BudgetService budgets) {
        return new ReportService(transactions, budgets);
    }
}
