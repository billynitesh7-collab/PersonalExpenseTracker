package com.expense;

import com.expense.server.AppServer;
import com.expense.storage.DataInitializer;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        DataInitializer.initialize(Path.of("data"));
        AppServer server = new AppServer();
        server.start();
        System.out.println("Personal Expense Tracker is running at http://localhost:8000");
        System.out.println("Press Ctrl+C to stop the server.");
    }
}
