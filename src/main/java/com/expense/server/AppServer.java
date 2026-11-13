package com.expense.server;

import com.expense.service.BudgetService;
import com.expense.service.ReportService;
import com.expense.service.TransactionService;
import com.expense.storage.JsonDataStore;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;

public class AppServer {
    private static final int PORT = 8000;
    private final HttpServer server;

    public AppServer() throws IOException {
        this(PORT, Path.of("data"));
    }

    /** The explicit port and data folder are also useful for isolated API tests. */
    public AppServer(int port, Path dataDirectory) throws IOException {
        // Bind explicitly to loopback so other devices on the network cannot connect.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        JsonDataStore dataStore = new JsonDataStore(dataDirectory);
        TransactionService transactionService = new TransactionService(dataStore);
        BudgetService budgetService = new BudgetService(dataStore, transactionService);
        ReportService reportService = new ReportService(transactionService, budgetService);
        server.createContext("/api/transactions", new TransactionApiHandler(transactionService));
        server.createContext("/api/budgets", new BudgetApiHandler(budgetService));
        server.createContext("/api/reports", new ReportApiHandler(reportService));

        // The API context is more specific; remaining GET requests serve frontend files only.
        Path frontendDirectory = Path.of("frontend");
        server.createContext("/", new StaticFileHandler(frontendDirectory));
    }

    public void start() {
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }
}
