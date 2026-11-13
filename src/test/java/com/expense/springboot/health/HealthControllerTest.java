package com.expense.springboot.health;

import com.expense.springboot.SpringBootExpenseApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = SpringBootExpenseApplication.class)
@AutoConfigureMockMvc
class HealthControllerTest {
    private static final Path TEST_DATA = createTestDirectory();
    @Autowired private MockMvc mockMvc;

    @DynamicPropertySource
    static void useTemporaryData(DynamicPropertyRegistry registry) {
        registry.add("expense.data-directory", () -> TEST_DATA.toString());
    }

    @AfterAll
    static void cleanTemporaryData() throws IOException {
        if (Files.exists(TEST_DATA)) try (var paths = Files.walk(TEST_DATA)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    @Test
    void springBootServesFrontendAndPreservesApiContracts() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"UP\"}"));
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<html")));
        var chart = mockMvc.perform(get("/vendor/chart.umd.min.js")).andExpect(status().isOk()).andReturn();
        assertTrue(chart.getResponse().getContentAsByteArray().length > 100_000);

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"income-1","type":"INCOME","amount":1000.10,"category":"Salary","date":"2026-11-06","description":"paycheck"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("income-1"))
                .andExpect(jsonPath("$.amount").value(1000.10));
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"expense-1","type":"EXPENSE","amount":0.20,"category":"Food","date":"2026-11-06","description":"lunch"}
                """))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/transactions/expense-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("expense-1"));
        mockMvc.perform(put("/api/transactions/expense-1").contentType(MediaType.APPLICATION_JSON).content("""
                {"id":"expense-1","type":"EXPENSE","amount":20.20,"category":"Food","date":"2026-11-06","description":"dinner"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(20.20));
        mockMvc.perform(get("/api/transactions?type=EXPENSE&category=Food&startDate=2026-11-01&endDate=2026-11-30&search=dinner"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("expense-1"));
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"EXPENSE","amount":0,"category":"Food","date":"2026-11-06","description":"bad"}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"EXPENSE","amount":1,"category":"Food","date":"2026-13-40","description":"bad date"}
                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/transactions/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Transaction not found."));
        mockMvc.perform(patch("/api/transactions")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(get("/api/budgets"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(get("/api/budgets?month=2026-11"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.budgetAmount").value(nullValue()));
        mockMvc.perform(put("/api/budgets/2026-11").contentType(MediaType.APPLICATION_JSON).content("""
                {"amount":10.25}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exceeded").value(true))
                .andExpect(jsonPath("$.remainingBudget").value(-9.95));
        mockMvc.perform(get("/api/budgets?month=2026-11"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usagePercentage").value(197.07));
        mockMvc.perform(get("/api/budgets"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].month").value("2026-11"));
        mockMvc.perform(put("/api/budgets/2026-11").contentType(MediaType.APPLICATION_JSON).content("""
                {"amount":0}
                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/budgets/2026-13").contentType(MediaType.APPLICATION_JSON).content("""
                {"amount":100}
                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/reports/summary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalIncome").value(1000.10))
                .andExpect(jsonPath("$.totalExpenses").value(20.20)).andExpect(jsonPath("$.netSavings").value(979.90));
        mockMvc.perform(get("/api/reports/daily?date=2026-11-06"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailySavings").value(979.90));
        mockMvc.perform(get("/api/reports/monthly?month=2026-11"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.monthlyExpenses").value(20.20))
                .andExpect(jsonPath("$.monthlyComparison.length()").value(12));
        mockMvc.perform(get("/api/reports/yearly?year=2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.yearlyIncome").value(1000.10))
                .andExpect(jsonPath("$.monthlyBreakdown.length()").value(12));
        mockMvc.perform(get("/api/reports/categories?month=2026-11"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categories[0].category").value("Food"))
                .andExpect(jsonPath("$.totalExpenses").value(20.20));
        mockMvc.perform(get("/api/reports/daily?date=2026-13-40"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());

        mockMvc.perform(delete("/api/transactions/income-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Transaction deleted."));
        mockMvc.perform(delete("/api/transactions/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").exists());
    }

    private static Path createTestDirectory() {
        try { return Files.createTempDirectory("expense-spring-api-test-"); }
        catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
}
