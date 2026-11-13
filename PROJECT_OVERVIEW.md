# Personal Expense Tracker — Project Overview

## Introduction

Personal Expense Tracker is a locally run browser application for recording income and expenses, planning monthly budgets, and reviewing financial summaries. It is built as a lightweight Java college project and keeps its data on the local computer.

## Problem statement

People often need a simple way to understand where money comes from and where it goes. A tracker should make it easy to record transactions, compare spending with a monthly limit, and review trends without requiring an online account or cloud service.

## Objectives

- Provide a clear dashboard for income, expenses, balance, and budget progress.
- Support transaction creation, editing, deletion, search, and filtering.
- Save monthly budgets and report budget usage and overspending.
- Summarize financial activity by day, month, year, and expense category.
- Demonstrate Java, HTTP, JSON file handling, and frontend integration with a manageable architecture.

## Main features

- Overview dashboard with totals, recent transactions, and charts.
- Transaction management with categories, dates, descriptions, search, and filters.
- Monthly budgets with remaining amounts, progress, and overspending status.
- Daily, monthly, yearly, and category reports.
- Indian Rupee formatting and exact decimal handling in Java and frontend financial calculations.
- Responsive dark-themed interface with locally bundled Chart.js.

## Technologies used

- Java 21 and com.sun.net.httpserver.HttpServer
- Gson 2.15.0, included in lib/gson-2.15.0.jar
- HTML, CSS, and vanilla JavaScript
- JavaScript BigInt decimal helper for frontend monetary arithmetic
- Chart.js bundled in frontend/vendor/chart.umd.min.js
- Local JSON files for persistence

No Node.js, npm, Maven, Gradle, database server, or cloud service is required.

## System architecture

The Java HTTP server binds to 127.0.0.1:8000. It serves the frontend files and handles the transaction, budget, and report APIs. The browser loads index.html; api.js communicates with the same-origin Java API, money.js provides exact decimal operations and INR formatting, and app.js controls page navigation, forms, data rendering, and charts.

On the backend, API handlers validate HTTP requests and delegate work to service classes. Services manage transaction and budget behavior and calculate reports. Model classes represent transactions and budgets. JsonDataStore reads and writes the JSON files, and DataInitializer creates missing files without overwriting existing ones. Static file requests are confined to the frontend directory.

## Java concepts demonstrated

- Classes and objects for transaction, budget, service, storage, and HTTP handler responsibilities.
- Encapsulation through private model fields and public constructors/getters/setters.
- An enum (TransactionType) for the two supported transaction types.
- Collections such as List, Map, ArrayList, and TreeMap for storing and grouping records.
- Exception handling for validation, API failures, and file operations.
- File creation, reading, temporary-file writing, and replacement using Java NIO.
- Gson serialization/deserialization with adapters for LocalDate and YearMonth.
- The JDK HTTP server, request routing, validation, and JSON responses.
- BigDecimal for money and records for report response structures.
- Separation of responsibilities across models, handlers, services, storage, utilities, and frontend files.

## Data storage

The project uses data/transactions.json and data/budgets.json. Each starts as an empty JSON array ([]). The application creates missing files on startup and does not overwrite files that already exist. Writes are prepared in a temporary file and moved into place, atomically when supported by the filesystem. Monetary values are represented by Java BigDecimal; transaction dates and budget months use ISO formats.

The JSON files are local and are not encrypted. Back them up if the application contains data that needs to be retained.

## API overview

### Transactions

- GET /api/transactions — list transactions; supports type, category, date-range, and search filters.
- POST /api/transactions — create a transaction.
- GET /api/transactions/{id} — retrieve one transaction.
- PUT /api/transactions/{id} — update a transaction.
- DELETE /api/transactions/{id} — delete a transaction.

### Budgets

- GET /api/budgets — retrieve saved monthly budgets and spending summaries.
- GET /api/budgets?month=YYYY-MM — retrieve one month's budget summary.
- PUT /api/budgets/YYYY-MM — create or update a monthly budget.

### Reports

- GET /api/reports/summary — overall totals and transaction count.
- GET /api/reports/daily?date=YYYY-MM-DD — daily totals.
- GET /api/reports/monthly?month=YYYY-MM — monthly totals and comparison.
- GET /api/reports/yearly?year=YYYY — yearly totals and monthly breakdown.
- GET /api/reports/categories?month=YYYY-MM — expense totals and percentages by category.

The API returns JSON and uses standard HTTP statuses for successful requests, validation errors, missing records, unsupported methods, and server errors.

## Limitations and future scope

- The application is for one local user and has no authentication, encryption, cloud synchronization, or automated backup.
- Graphical browser testing has not been verified; source checks, simulated frontend tests, API checks, and Java tests have been run.
- Chart geometry uses numeric coordinates for drawing, while financial totals, displayed labels, and tooltips preserve decimal arithmetic.
- Possible future work could include user-configurable categories, export/backup tools, and additional report views, while keeping the local-first design.
