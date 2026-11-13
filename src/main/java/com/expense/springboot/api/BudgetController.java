package com.expense.springboot.api;

import com.expense.model.BudgetSummary;
import com.expense.service.BudgetService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService service;
    public BudgetController(BudgetService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<?> getBudgets(HttpServletRequest request) throws IOException {
        Map<String, String> params = ApiRequestParser.parseQuery(request.getQueryString());
        ApiRequestParser.requireOnlyAllowedQuery(params, "month");
        String value = params.get("month");
        if (value == null) return ApiResponses.json(HttpStatus.OK, service.getAllBudgetSummaries());
        if (value.isBlank()) throw new IllegalArgumentException("Month cannot be empty.");
        YearMonth month = ApiRequestParser.parseMonth(value, "month");
        return ApiResponses.json(HttpStatus.OK, service.getBudgetSummary(month));
    }

    @PutMapping("/{month}")
    public ResponseEntity<BudgetSummary> saveBudget(@PathVariable String month, @RequestBody(required = false) String body) throws IOException {
        YearMonth parsed = ApiRequestParser.parseMonth(month, "Month in the URL");
        service.createOrUpdateBudget(parsed, ApiRequestParser.readBudgetAmount(body));
        return ApiResponses.json(HttpStatus.OK, service.getBudgetSummary(parsed));
    }

    @RequestMapping
    public ResponseEntity<Map<String, String>> unsupportedCollectionMethod() {
        return ApiResponses.methodNotAllowed("GET");
    }

    @RequestMapping("/{month}")
    public ResponseEntity<Map<String, String>> unsupportedMonthMethod() {
        return ApiResponses.methodNotAllowed("PUT");
    }

    @RequestMapping("/**")
    public ResponseEntity<Map<String, String>> unknownRoute() {
        return ApiResponses.error(HttpStatus.NOT_FOUND, "API route not found.");
    }
}
