package com.expense.springboot.api;

import com.expense.model.Transaction;
import com.expense.springboot.auth.AccountPrincipal;
import com.expense.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService service;
    public TransactionController(TransactionService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<Transaction>> getTransactions(HttpServletRequest request, @AuthenticationPrincipal AccountPrincipal user) throws IOException {
        var filters = ApiRequestParser.parseTransactionFilters(request.getQueryString());
        return ApiResponses.json(HttpStatus.OK, service.searchTransactions(filters.type(), filters.category(),
                filters.startDate(), filters.endDate(), filters.search(), user.id()));
    }

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@RequestBody(required = false) String body,
                                                          @AuthenticationPrincipal AccountPrincipal user) throws IOException {
        return ApiResponses.json(HttpStatus.CREATED, service.addTransaction(ApiRequestParser.readTransaction(body), user.id()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable String id, @AuthenticationPrincipal AccountPrincipal user) throws IOException {
        return ApiResponses.json(HttpStatus.OK, service.findTransaction(id, user.id()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transaction> updateTransaction(@PathVariable String id, @RequestBody(required = false) String body,
                                                         @AuthenticationPrincipal AccountPrincipal user) throws IOException {
        return ApiResponses.json(HttpStatus.OK, service.updateTransaction(id, ApiRequestParser.readTransaction(body), user.id()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTransaction(@PathVariable String id, @AuthenticationPrincipal AccountPrincipal user) throws IOException {
        service.deleteTransaction(id, user.id());
        return ApiResponses.json(HttpStatus.OK, Map.of("message", "Transaction deleted.", "id", id));
    }

    @RequestMapping
    public ResponseEntity<Map<String, String>> unsupportedCollectionMethod() {
        return ApiResponses.methodNotAllowed("GET, POST");
    }

    @RequestMapping("/{id}")
    public ResponseEntity<Map<String, String>> unsupportedItemMethod() {
        return ApiResponses.methodNotAllowed("GET, PUT, DELETE");
    }

    @RequestMapping("/**")
    public ResponseEntity<Map<String, String>> unknownRoute() {
        return ApiResponses.error(HttpStatus.NOT_FOUND, "API route not found.");
    }
}
