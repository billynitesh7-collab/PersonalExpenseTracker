package com.expense.springboot.api;

import com.expense.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(HttpServletRequest request) throws IOException {
        if (!ApiRequestParser.parseQuery(request.getQueryString()).isEmpty()) {
            throw new IllegalArgumentException("The summary endpoint does not accept query parameters.");
        }
        return ApiResponses.json(HttpStatus.OK, service.getFinancialSummary());
    }

    @GetMapping("/daily")
    public ResponseEntity<?> daily(HttpServletRequest request) throws IOException {
        String value = ApiRequestParser.parseOnlyQuery(request.getQueryString(), "date").get("date");
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Date must use YYYY-MM-DD format.");
        LocalDate date;
        try { date = LocalDate.parse(value); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException("Date must be a valid calendar date."); }
        return ApiResponses.json(HttpStatus.OK, service.getDailyReport(date));
    }

    @GetMapping("/monthly")
    public ResponseEntity<?> monthly(HttpServletRequest request) throws IOException {
        Map<String, String> params = ApiRequestParser.parseOnlyQuery(request.getQueryString(), "month");
        YearMonth month = ApiRequestParser.parseMonth(params.get("month"), "Month");
        return ApiResponses.json(HttpStatus.OK, service.getMonthlyReport(month));
    }

    @GetMapping("/yearly")
    public ResponseEntity<?> yearly(HttpServletRequest request) throws IOException {
        Map<String, String> params = ApiRequestParser.parseOnlyQuery(request.getQueryString(), "year");
        Year year = ApiRequestParser.parseYear(params.get("year"));
        return ApiResponses.json(HttpStatus.OK, service.getYearlyReport(year));
    }

    @GetMapping("/categories")
    public ResponseEntity<?> categories(HttpServletRequest request) throws IOException {
        Map<String, String> params = ApiRequestParser.parseOnlyQuery(request.getQueryString(), "month");
        YearMonth month = ApiRequestParser.parseMonth(params.get("month"), "Month");
        return ApiResponses.json(HttpStatus.OK, service.getCategoryReport(month));
    }

    @RequestMapping("/{endpoint}")
    public ResponseEntity<Map<String, String>> unsupportedOrUnknownEndpoint(@org.springframework.web.bind.annotation.PathVariable String endpoint) {
        if (java.util.Set.of("summary", "daily", "monthly", "yearly", "categories").contains(endpoint)) {
            return ApiResponses.methodNotAllowed("GET");
        }
        return ApiResponses.error(HttpStatus.NOT_FOUND, "Report endpoint not found.");
    }

    @RequestMapping("/**")
    public ResponseEntity<Map<String, String>> unknownNestedRoute() {
        return ApiResponses.error(HttpStatus.NOT_FOUND, "Report endpoint not found.");
    }
}
