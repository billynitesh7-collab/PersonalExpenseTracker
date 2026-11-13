# Personal Expense Tracker

A local, browser-based personal finance tracker built with Java 21 and plain HTML, CSS, and JavaScript. The Java application serves the frontend and a small JSON API from `127.0.0.1:8000`. Transactions and monthly budgets are stored as JSON files in the project directory.

## Project objectives

- Record and manage income and expense transactions.
- Set monthly budgets and compare them with recorded expenses.
- Review financial summaries and category spending reports.
- Keep data on the local Mac rather than using a cloud service.
- Use a small, beginner-friendly Java server without a large backend framework.

## Technologies

- Java 21 and the JDK built-in `com.sun.net.httpserver.HttpServer` (existing application)
- Spring Boot 4.1.2 Web MVC (separate, incremental migration entry point)
- Gson 2.15.0 for JSON conversion
- HTML, CSS, and vanilla JavaScript
- Chart.js, included locally under `frontend/vendor`
- JSON files for persistent data

## Architecture

The existing application remains the browser-facing server: `com.expense.Main` starts the JDK `HttpServer` on `127.0.0.1:8000`, serves the frontend, and exposes the transaction, budget, and report APIs. Its handlers, services, and `JsonDataStore` retain their existing responsibilities.

A separate Spring Boot entry point, `com.expense.springboot.SpringBootExpenseApplication`, currently exposes only `GET /api/health` on `127.0.0.1:8081` by default. It does not replace or call the existing server, APIs, services, or JSON storage. Set the `PORT` environment variable to select another port. Neither entry point includes user accounts or authentication.

## Project structure

```text
PersonalExpenseTracker_MultiUser/
├── data/
│   ├── transactions.json       # Saved income and expense records
│   └── budgets.json            # Saved monthly budgets
├── frontend/
│   ├── index.html
│   ├── css/styles.css
│   ├── js/money.js             # Exact decimal arithmetic and INR formatting
│   ├── js/api.js
│   ├── js/app.js
│   └── vendor/chart.umd.min.js
├── pom.xml                     # Maven build and dependency configuration
├── mvnw, mvnw.cmd               # Maven Wrapper launchers
├── .mvn/wrapper/                # Pinned Maven distribution settings
├── lib/gson-2.15.0.jar          # Retained for the earlier manual setup; Maven uses its dependency
├── src/
│   ├── main/
│   │   ├── java/com/expense/
│   │   │   ├── Main.java
│   │   │   ├── model/          # Transaction, budget, and type models
│   │   │   ├── server/         # Existing HTTP server and handlers
│   │   │   ├── service/        # Transaction, budget, and report logic
│   │   │   ├── storage/        # JSON file access and initialization
│   │   │   ├── util/           # Gson date/month configuration
│   │   │   └── springboot/     # Independent Spring Boot application and health API
│   │   └── resources/application.properties # Spring Boot port and local bind address
│   └── test/java/com/expense/
│       ├── server/, storage/   # Existing standalone tests and Maven adapters
│       └── springboot/         # Spring Boot health endpoint test
├── .vscode/settings.json      # Java source, output, and local JAR settings
└── README.md
```

## Features

- Overview cards for balance, income, expenses, and current-month budget
- Transaction create, edit, delete, search, and filtering
- Monthly budgets with spending progress and overspending indicator
- Daily, monthly, yearly, and category reports
- Locally stored Chart.js charts with INR formatting
- Responsive dark dashboard layout
- Persistent transaction and budget data stored in project-local JSON files

## Prerequisites

- macOS and JDK 21 (`java` and `javac` available in Terminal)
- Internet access on the first Maven Wrapper build so it can download the pinned Maven distribution and Maven dependencies
- Visual Studio Code is optional

Check Java with:

```sh
java -version
javac -version
```

Both should report version 21. A separate Maven installation is not required; use the included `./mvnw` wrapper on macOS or `mvnw.cmd` on Windows.

## Gson and Maven setup

Gson 2.15.0 is declared as a Maven dependency in `pom.xml`. The wrapper downloads it into the normal local Maven repository when needed. The existing `lib/gson-2.15.0.jar` is retained for compatibility with the earlier manual setup, but the Maven build does not use that JAR directly. No Maven installation or VS Code classpath setup is required.

## Clean build, tests, and run on macOS

Run the following from the project root. The first invocation downloads Maven 3.9.11 to the user's Maven Wrapper cache; later runs reuse it.

```sh
./mvnw clean package
./mvnw test
```

`clean package` compiles both entry points and runs the complete test suite. `test` reruns the tests. The four existing standalone Java test programs are invoked by JUnit adapters, and the Spring Boot health endpoint has its own MVC test. Stop a running server with **Ctrl+C**.

### Existing application (port 8000)

```sh
./mvnw exec:java -Dexec.mainClass=com.expense.Main
```

Run from the project root because this application resolves `frontend/` and `data/` relative to that directory. On first run, it creates missing JSON files but does not replace existing ones. Open [http://localhost:8000](http://localhost:8000).

### Separate Spring Boot application (port 8081)

```sh
./mvnw spring-boot:run -Dspring-boot.run.main-class=com.expense.springboot.SpringBootExpenseApplication
```

This app currently exposes only `GET /api/health`; it does not manage financial data. The default port comes from `PORT` (default `8081`), for example `PORT=8090 ./mvnw spring-boot:run -Dspring-boot.run.main-class=com.expense.springboot.SpringBootExpenseApplication`. It binds to loopback (`127.0.0.1`). The health URL is [http://127.0.0.1:8081/api/health](http://127.0.0.1:8081/api/health), returning `{"status":"UP"}`.

## API endpoints

All endpoints are served by the same local Java process. Successful API responses are JSON. Error responses use an `error` property and appropriate HTTP status codes.

### Transactions

| Method | Endpoint | Purpose | Success status |
|---|---|---|---:|
| GET | `/api/transactions` | List transactions; accepts optional `type`, `category`, `startDate`, `endDate`, and `search` query parameters | 200 |
| POST | `/api/transactions` | Add a transaction | 201 |
| GET | `/api/transactions/{id}` | Retrieve one transaction | 200 |
| PUT | `/api/transactions/{id}` | Replace a transaction's fields | 200 |
| DELETE | `/api/transactions/{id}` | Delete a transaction | 200 |

Transaction JSON uses `id`, `type` (`INCOME` or `EXPENSE`), `amount`, `category`, `date` (`YYYY-MM-DD`), and `description`. The server assigns an ID when a new transaction omits it. Amounts are represented in Java with `BigDecimal`.

### Budgets

| Method | Endpoint | Purpose | Success status |
|---|---|---|---:|
| GET | `/api/budgets` | List saved monthly budgets with spending summaries | 200 |
| GET | `/api/budgets?month=YYYY-MM` | Retrieve one month's budget and spending summary | 200 |
| PUT | `/api/budgets/YYYY-MM` | Create or update that month's budget; JSON body: `{"amount": 50000.00}` | 200 |

A monthly summary includes `month`, `budgetAmount`, `totalExpenses`, `remainingBudget`, `usagePercentage`, and `exceeded`. If there is no budget for a month, the summary represents the missing budget with a null `budgetAmount` and still reports monthly expenses.

### Reports

| Method | Endpoint | Purpose | Success status |
|---|---|---|---:|
| GET | `/api/reports/summary` | Overall income, expenses, savings, and transaction count | 200 |
| GET | `/api/reports/daily?date=YYYY-MM-DD` | One day's totals | 200 |
| GET | `/api/reports/monthly?month=YYYY-MM` | One month's totals and month-by-month comparison | 200 |
| GET | `/api/reports/yearly?year=YYYY` | One year's totals and 12-month breakdown | 200 |
| GET | `/api/reports/categories?month=YYYY-MM` | Expense totals and percentages by category | 200 |

Invalid request values return 400. A missing transaction returns 404; unsupported methods return 405; unexpected server or storage errors return a generic 500 response without a stack trace or local file path.

## JSON storage

`data/transactions.json` stores an array of transaction objects. `data/budgets.json` stores an array of monthly budget objects. Dates use ISO strings (`YYYY-MM-DD` for transaction dates and `YYYY-MM` for budget months). Money is serialized as a JSON number and handled as `BigDecimal` in Java.

Writes are prepared in temporary files in the data directory and then moved over the destination, using an atomic replacement when the filesystem supports it. Keep these files inside the project to retain the data. Back them up before manually editing them. The application does not encrypt the JSON files.

## Run the Java tests

Run all four existing test programs through the Maven test lifecycle:

```sh
./mvnw test
```

The tests use temporary data directories and do not use the project's saved financial records. Surefire reports are written under `target/surefire-reports/`.

## Frontend syntax check

If JavaScriptCore is available on macOS, check the application scripts with:

```sh
for file in frontend/js/*.js; do
  /System/Library/Frameworks/JavaScriptCore.framework/Versions/A/Helpers/jsc -e "new Function(readFile('$file'))" || exit 1
done
```

If `jsc` is unavailable on the Mac, use any already-installed JavaScript runtime; Node.js is not required by the application.

## Known limitations

- The application is designed for one local user and has no sign-in, authentication, or encryption for the JSON files.
- There is no cloud synchronization or backup service. Copy the `data/` directory to make a backup.
- Frontend money arithmetic uses a small `BigInt` decimal helper and preserves decimal JSON tokens. Chart.js converts values to numeric coordinates for drawing, so chart geometry is approximate at extreme precision; chart labels and tooltips use the exact decimal values. Currency display rounds to two decimal places using half-up rounding, while stored values are not rounded by the frontend.
- Graphical browser verification has not been completed. Source checks, JavaScriptCore simulations, HTTP checks, and Java tests have been performed, but actual visual layout, interaction, and browser-console behavior remain unverified.
- Chart.js is bundled locally, so the browser does not need a network connection to load the chart library.
