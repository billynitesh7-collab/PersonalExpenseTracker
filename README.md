# Personal Expense Tracker

A browser-based personal finance tracker built with Java 21, Spring Boot, PostgreSQL, and plain HTML, CSS, and JavaScript.

## Project objectives

- Record and manage income and expense transactions.
- Set monthly budgets and compare them with recorded expenses.
- Review financial summaries and category spending reports.
- Store application data in PostgreSQL with exact decimal amounts.
- Keep the original lightweight Java HTTP server available as a local backup.

## Technologies

- Java 21 and Spring Boot 4.1.2 Web MVC/JDBC (primary application)
- PostgreSQL JDBC driver and Flyway migrations
- JDK `com.sun.net.httpserver.HttpServer` (legacy backup)
- Gson 2.15.0 for JSON conversion
- HTML, CSS, and vanilla JavaScript
- Chart.js, included locally under `frontend/vendor`
- PostgreSQL for Spring Boot production data; JSON remains only for the legacy backup

## Architecture

Spring Boot is the primary server: `com.expense.springboot.SpringBootExpenseApplication` serves the frontend, APIs, and health endpoint on `127.0.0.1:8081` by default. Controllers reuse the existing services through `FinanceDataStore`; Spring Boot uses its PostgreSQL implementation, and Flyway runs versioned migrations at startup. Database connection settings come from `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.

The original `com.expense.Main` and JDK `HttpServer` remain available as a JSON-backed backup on `127.0.0.1:8000`. Spring Boot uses PostgreSQL-backed accounts and financial data. Account registration is public; sign-in, sign-out, and every transaction, budget, and report API use authenticated server-side sessions. The frontend and APIs are same-origin.

## Project structure

```text
PersonalExpenseTracker_MultiUser/
├── .env.example                # Placeholder database settings (not loaded automatically)
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
│   │   │   ├── storage/        # Shared store interface, legacy JSON and PostgreSQL adapters
│   │   │   ├── util/           # Gson date/month configuration
│   │   │   └── springboot/     # Independent Spring Boot application and health API
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/    # Versioned Flyway schema migrations
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
- Persistent transaction and budget data stored in PostgreSQL by Spring Boot

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

`clean package` compiles both entry points and runs the test suite. `test` reruns it. The four existing standalone Java test programs run through JUnit adapters; Spring MVC and PostgreSQL integration tests cover health, authentication, CSRF, and user isolation. Configure the dedicated test database variables below before running the full suite. Stop a running server with **Ctrl+C**.

### Local PostgreSQL setup

Install and start PostgreSQL with a local package manager, then create a dedicated application database and role. For example, from `psql` as a local administrator:

```sql
CREATE ROLE expense_tracker LOGIN PASSWORD 'choose-a-local-password';
CREATE DATABASE expense_tracker OWNER expense_tracker;
```

Copy `.env.example` to `.env` as a reminder of the setting names, replace the placeholders locally, and export the values in the terminal before starting Spring Boot. Spring Boot does not load `.env` automatically; do not commit `.env`.

```sh
export DATABASE_URL='jdbc:postgresql://localhost:5432/expense_tracker'
export DATABASE_USERNAME='expense_tracker'
export DATABASE_PASSWORD='your-local-password'
export SPRING_PROFILES_ACTIVE=prod
```

Flyway applies `src/main/resources/db/migration/V1__create_finance_tables.sql` automatically when the Spring Boot application starts. Starting the application after exporting the variables above runs pending migrations before the API becomes available. Do not point it at a database that should not be changed.

To run the PostgreSQL migration, constraint, decimal, and persistence integration test, configure a separate disposable test database with permission to create and drop schemas:

```sh
export POSTGRES_TEST_URL='jdbc:postgresql://localhost:5432/expense_tracker_test'
export POSTGRES_TEST_USERNAME='your-test-database-role'
export POSTGRES_TEST_PASSWORD='your-test-database-password'
./mvnw -Dtest=PostgresPersistenceIntegrationTest test
./mvnw test
```

The tests create uniquely named schemas or synthetic accounts, apply/check Flyway migrations, and clean up their test data. Use only a dedicated test database that contains no personal financial records. Do not run tests against production data.

### Primary application: Spring Boot (port 8081)

```sh
./mvnw spring-boot:run -Dspring-boot.run.main-class=com.expense.springboot.SpringBootExpenseApplication
```

Open [http://localhost:8081](http://localhost:8081). Spring Boot serves the frontend, local Chart.js, and all documented APIs. `GET /api/health` returns `{"status":"UP"}`. The port defaults to `8081`; change it with `PORT`, for example `PORT=8090 ./mvnw spring-boot:run -Dspring-boot.run.main-class=com.expense.springboot.SpringBootExpenseApplication`.

### Legacy backup application (port 8000)

```sh
./mvnw exec:java -Dexec.mainClass=com.expense.Main
```

This backup remains available but is not needed when using Spring Boot. Run it from the project root because its frontend and `data/` paths are relative to that directory.

## API endpoints

All endpoints are served by the same local Java process. Successful API responses are JSON. Error responses use an `error` property and appropriate HTTP status codes.

### Authentication

| Method | Endpoint | Purpose | Success status |
|---|---|---|---:|
| GET | `/api/auth/csrf` | Create/read the session CSRF token for the JavaScript client | 200 |
| POST | `/api/auth/register` | Create an account; JSON body contains `email` and `password` | 201 |
| POST | `/api/auth/login` | Start an authenticated server-side session | 200 |
| POST | `/api/auth/logout` | Invalidate the current session; requires CSRF header | 204 |
| GET | `/api/auth/me` | Return the current account; requires authentication | 200 |

Registration and login requests also require the CSRF header. Obtain its name and value from `/api/auth/csrf`; the browser client handles this automatically. Registration passwords must be at least 12 characters and no more than 72 UTF-8 bytes.

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

## PostgreSQL storage

Spring Boot stores users, transactions, and budgets in PostgreSQL. Monetary fields use unconstrained PostgreSQL `NUMERIC`, mapped to Java `BigDecimal`; dates and monthly periods use SQL date/text values. Flyway records applied migrations in its schema history table. The legacy server continues to use JSON in `data/`, but it is a separate backup and is not used by Spring Boot.

Flyway V1 creates the schema and V2 requires a valid owner for every transaction and budget. V2 aborts if it finds unowned rows; it never assigns financial data to an account automatically. The database enforces ownership foreign keys, and budgets are unique per user/month. No personal JSON records are imported by this migration.

## Accounts and session security

- `POST /api/auth/register` creates an account; passwords are stored using BCrypt hashes.
- `POST /api/auth/login` authenticates and establishes an HTTP session.
- `POST /api/auth/logout` ends the session; `GET /api/auth/me` returns the signed-in account.
- `GET /api/auth/csrf` supplies the CSRF token used by the JavaScript client in `X-CSRF-TOKEN` for state-changing requests.
- Session cookies are HttpOnly and SameSite=Strict. Activate the `prod` profile behind HTTPS to set the Secure cookie flag.
- Financial controllers derive the owner ID from the authenticated Spring Security principal. They do not accept an owner ID from the browser; looking up another user's transaction returns the same 404 as an unknown ID.

Registration has no email verification or password-reset workflow yet. Do not expose the application to the public internet until deployed behind HTTPS with production database credentials and operational safeguards.

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

- No graphical browser verification has been completed. Authentication, CSRF, and ownership are covered by automated Spring MVC tests against PostgreSQL.
- The legacy server stores JSON separately; Spring Boot uses PostgreSQL. No automatic data import or synchronization exists.
- Frontend money arithmetic uses a small `BigInt` decimal helper and preserves decimal JSON tokens. Chart.js converts values to numeric coordinates for drawing, so chart geometry is approximate at extreme precision; chart labels and tooltips use the exact decimal values. Currency display rounds to two decimal places using half-up rounding, while stored values are not rounded by the frontend.
- Graphical browser verification has not been completed. Source checks, JavaScriptCore simulations, HTTP checks, and Java tests have been performed, but actual visual layout, interaction, and browser-console behavior remain unverified.
- Chart.js is bundled locally, so the browser does not need a network connection to load the chart library.
