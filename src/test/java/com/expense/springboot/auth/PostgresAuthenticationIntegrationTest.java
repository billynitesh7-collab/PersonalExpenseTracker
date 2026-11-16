package com.expense.springboot.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL + Spring Security test. Uses only synthetic accounts and records. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class PostgresAuthenticationIntegrationTest {
    private static final String DB_URL = System.getenv().getOrDefault("POSTGRES_TEST_URL",
            "jdbc:postgresql://127.0.0.1:5432/expense_tracker_test");
    private static final String DB_USER = System.getenv().getOrDefault("POSTGRES_TEST_USERNAME", "billynitesh");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("POSTGRES_TEST_PASSWORD", "");
    private static final String PASSWORD = "test-password-123";
    private static final String MONTH = "2026-11";
    private static final String DATE = "2026-11-13";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB_URL);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    private final List<String> createdEmails = new ArrayList<>();

    @AfterEach
    void removeSyntheticAccounts() {
        for (String email : createdEmails) jdbc.update("DELETE FROM users WHERE email = ?", email);
        createdEmails.clear();
    }

    @Test
    void csrfAuthenticationAndTwoUserFinancialIsolation() throws Exception {
        mvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/budgets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reports/summary")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"x@example.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());

        String suffix = UUID.randomUUID().toString();
        String emailA = "alice-" + suffix + "@example.test";
        String emailB = "blake-" + suffix + "@example.test";
        createdEmails.add(emailA);
        createdEmails.add(emailB);

        MockHttpSession registerSession = new MockHttpSession();
        String csrf = csrf(registerSession);
        mvc.perform(post("/api/auth/register").session(registerSession).header("X-CSRF-TOKEN", csrf)
                        .contentType("application/json").content(credentials(emailA)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(emailA));
        mvc.perform(post("/api/auth/register").session(registerSession).header("X-CSRF-TOKEN", csrf)
                        .contentType("application/json").content(credentials(emailB)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(emailB));
        mvc.perform(post("/api/auth/register").session(registerSession).header("X-CSRF-TOKEN", csrf)
                        .contentType("application/json").content(credentials(emailA)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/login").session(registerSession).header("X-CSRF-TOKEN", csrf)
                        .contentType("application/json").content("{\"email\":\"" + emailA + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        String hashA = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, emailA);
        assertNotEquals(PASSWORD, hashA);
        assertTrue(hashA.startsWith("$2"));
        assertTrue(encoder.matches(PASSWORD, hashA));

        LoggedIn userA = login(emailA);
        LoggedIn userB = login(emailB);
        mvc.perform(get("/api/auth/me").session(userA.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailA));

        String txA = createTransaction(userA, "INCOME", "125000.55", "Salary", "{" +
                "\"amount\":125000.55,\"category\":\"Salary\",\"description\":\"synthetic income\"}");
        String expenseA = createTransaction(userA, "EXPENSE", "0.10", "Food",
                "{\"amount\":0.10,\"category\":\"Food\",\"description\":\"synthetic food\"}");
        createTransaction(userB, "EXPENSE", "7.25", "Travel",
                "{\"amount\":7.25,\"category\":\"Travel\",\"description\":\"synthetic travel\",\"userId\":1}");

        mvc.perform(get("/api/transactions").session(userA.session())).andExpect(status().isOk())
                .andExpect(content().string(containsString("synthetic income")))
                .andExpect(content().string(containsString("synthetic food")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("synthetic travel"))));
        mvc.perform(get("/api/transactions/{id}", txA).session(userB.session())).andExpect(status().isNotFound());
        mvc.perform(put("/api/transactions/{id}", expenseA).session(userB.session())
                        .header("X-CSRF-TOKEN", userB.csrf()).contentType("application/json")
                        .content("{\"type\":\"EXPENSE\",\"amount\":99,\"category\":\"Food\",\"date\":\"" + DATE + "\",\"description\":\"probe\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/transactions/{id}", expenseA).session(userB.session())
                        .header("X-CSRF-TOKEN", userB.csrf())).andExpect(status().isNotFound());

        mvc.perform(put("/api/budgets/{month}", MONTH).session(userA.session())
                        .header("X-CSRF-TOKEN", userA.csrf()).contentType("application/json")
                        .content("{\"amount\":0.05}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalExpenses").value(0.10));
        mvc.perform(put("/api/budgets/{month}", MONTH).session(userB.session())
                        .header("X-CSRF-TOKEN", userB.csrf()).contentType("application/json")
                        .content("{\"amount\":10.00}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalExpenses").value(7.25));
        mvc.perform(get("/api/budgets").queryParam("month", MONTH).session(userA.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.budgetAmount").value(0.05));
        mvc.perform(get("/api/budgets").queryParam("month", MONTH).session(userB.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.budgetAmount").value(10.00));

        mvc.perform(get("/api/reports/summary").session(userA.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(125000.55))
                .andExpect(jsonPath("$.totalExpenses").value(0.10))
                .andExpect(jsonPath("$.netSavings").value(125000.45));
        mvc.perform(get("/api/reports/summary").session(userB.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(0))
                .andExpect(jsonPath("$.totalExpenses").value(7.25));
        mvc.perform(get("/api/reports/daily").queryParam("date", DATE).session(userA.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailyExpenses").value(0.10));
        mvc.perform(get("/api/reports/monthly").queryParam("month", MONTH).session(userB.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.monthlyExpenses").value(7.25));
        mvc.perform(get("/api/reports/yearly").queryParam("year", "2026").session(userA.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.yearlyIncome").value(125000.55));
        mvc.perform(get("/api/reports/categories").queryParam("month", MONTH).session(userA.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalExpenses").value(0.10))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Travel"))));

        mvc.perform(post("/api/transactions").session(userA.session()).contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(userA.session()).header("X-CSRF-TOKEN", userA.csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/transactions").session(userA.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedApiPreservesCrudValidationReportsAndStaticServing() throws Exception {
        String email = "contract-" + UUID.randomUUID() + "@example.test";
        createdEmails.add(email);
        LoggedIn user = registerAndLogin(email);

        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(containsString("auth-screen")));
        assertTrue(mvc.perform(get("/vendor/chart.umd.min.js")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray().length > 100_000);

        String incomeId = createTransaction(user, "INCOME", "1000.10", "Salary",
                "{\"amount\":1000.10,\"category\":\"Salary\",\"description\":\"synthetic paycheck\"}");
        String expenseId = createTransaction(user, "EXPENSE", "20.20", "Food",
                "{\"amount\":20.20,\"category\":\"Food\",\"description\":\"synthetic lunch\"}");
        mvc.perform(get("/api/transactions").session(user.session()).queryParam("type", "EXPENSE")
                        .queryParam("category", "Food").queryParam("startDate", "2026-11-01")
                        .queryParam("endDate", "2026-11-30").queryParam("search", "lunch"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(expenseId));

        mvc.perform(put("/api/transactions/{id}", expenseId).session(user.session())
                        .header("X-CSRF-TOKEN", user.csrf()).contentType("application/json")
                        .content("{\"id\":\"" + expenseId + "\",\"type\":\"EXPENSE\",\"amount\":20.20," +
                                "\"category\":\"Food\",\"date\":\"2026-11-13\",\"description\":\"synthetic dinner\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("synthetic dinner"));
        mvc.perform(post("/api/transactions").session(user.session()).header("X-CSRF-TOKEN", user.csrf())
                        .contentType("application/json").content("{\"type\":\"EXPENSE\",\"amount\":0," +
                                "\"category\":\"Food\",\"date\":\"2026-11-13\",\"description\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/transactions").session(user.session()).header("X-CSRF-TOKEN", user.csrf())
                        .contentType("application/json").content("{\"type\":\"EXPENSE\",\"amount\":1," +
                                "\"category\":\"Food\",\"date\":\"2026-13-40\",\"description\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/transactions/not-a-record").session(user.session())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/transactions").session(user.session()).header("X-CSRF-TOKEN", user.csrf()))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(get("/api/budgets").session(user.session())).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/budgets").session(user.session()).queryParam("month", MONTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.budgetAmount").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(put("/api/budgets/{month}", MONTH).session(user.session()).header("X-CSRF-TOKEN", user.csrf())
                        .contentType("application/json").content("{\"amount\":10.25}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exceeded").value(true))
                .andExpect(jsonPath("$.remainingBudget").value(-9.95))
                .andExpect(jsonPath("$.usagePercentage").value(197.07));
        mvc.perform(put("/api/budgets/{month}", MONTH).session(user.session()).header("X-CSRF-TOKEN", user.csrf())
                        .contentType("application/json").content("{\"amount\":50.00}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exceeded").value(false));

        mvc.perform(get("/api/reports/summary").session(user.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(1000.10)).andExpect(jsonPath("$.totalExpenses").value(20.20))
                .andExpect(jsonPath("$.netSavings").value(979.90));
        mvc.perform(get("/api/reports/daily").session(user.session()).queryParam("date", DATE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailySavings").value(979.90));
        mvc.perform(get("/api/reports/monthly").session(user.session()).queryParam("month", MONTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.monthlyExpenses").value(20.20))
                .andExpect(jsonPath("$.monthlyComparison.length()").value(12));
        mvc.perform(get("/api/reports/yearly").session(user.session()).queryParam("year", "2026"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.yearlyIncome").value(1000.10))
                .andExpect(jsonPath("$.monthlyBreakdown.length()").value(12));
        mvc.perform(get("/api/reports/categories").session(user.session()).queryParam("month", MONTH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categories[0].category").value("Food"))
                .andExpect(jsonPath("$.totalExpenses").value(20.20));
        mvc.perform(get("/api/reports/daily").session(user.session()).queryParam("date", "2026-13-40"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/transactions/{id}", incomeId).session(user.session())
                        .header("X-CSRF-TOKEN", user.csrf())).andExpect(status().isOk());
        mvc.perform(delete("/api/transactions/missing").session(user.session())
                        .header("X-CSRF-TOKEN", user.csrf())).andExpect(status().isNotFound());
    }

    private String createTransaction(LoggedIn user, String type, String amount, String category, String body) throws Exception {
        MvcResult result = mvc.perform(post("/api/transactions").session(user.session())
                        .header("X-CSRF-TOKEN", user.csrf()).contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"date\":\"" + DATE + "\"," + body.substring(1)))
                .andExpect(status().isCreated()).andReturn();
        JsonObject response = JsonParser.parseString(result.getResponse().getContentAsString()).getAsJsonObject();
        assertEquals(new java.math.BigDecimal(amount), response.get("amount").getAsBigDecimal());
        return response.get("id").getAsString();
    }

    private LoggedIn login(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String csrf = csrf(session);
        MvcResult result = mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", csrf)
                        .contentType("application/json").content(credentials(email)))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession authenticated = (MockHttpSession) result.getRequest().getSession(false);
        return new LoggedIn(authenticated, csrf(authenticated));
    }

    private LoggedIn registerAndLogin(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String csrf = csrf(session);
        mvc.perform(post("/api/auth/register").session(session).header("X-CSRF-TOKEN", csrf)
                        .contentType("application/json").content(credentials(email)))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String csrf(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        return JsonParser.parseString(result.getResponse().getContentAsString()).getAsJsonObject().get("token").getAsString();
    }

    private static String credentials(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    private record LoggedIn(MockHttpSession session, String csrf) { }
}
