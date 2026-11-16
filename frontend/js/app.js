/* Transactions and budgets are loaded from the local Java APIs. */
const CATEGORIES = ["Food", "Travel", "Shopping", "Education", "Healthcare", "Entertainment", "Bills", "Other"];
const CATEGORY_STYLE = {
  Food: { color: "#e7a86a", icon: "◌" }, Travel: { color: "#7eafe8", icon: "↗" }, Shopping: { color: "#b99ce9", icon: "◇" },
  Education: { color: "#72c4bd", icon: "▤" }, Healthcare: { color: "#e88d98", icon: "+" }, Entertainment: { color: "#e3c16b", icon: "♪" },
  Bills: { color: "#86a4b0", icon: "▣" }, Other: { color: "#a5aca5", icon: "·" }
};
const today = new Date();
const todayISO = toISODate(today);
const MONTH_LABEL = new Intl.DateTimeFormat("en", { month: "long", year: "numeric" });
let transactions = [];
let transactionsLoaded = false;
let budgets = [];
let budgetSummaries = new Map();
let budgetsLoaded = false;
let selectedBudgetMonth = currentMonthKey();
let budgetRequestNumber = 0;
let cashflowChart;
let categoryChart;
let reportCashflowChart;
let reportCategoryChart;
let toastTimeout;
let filterTimeout;
let filterRequestNumber = 0;
let reportsRequestNumber = 0;
let authenticatedUser = null;
let appInitialized = false;

function toISODate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
}

function money(value, compact = false) {
  return FinanceDecimal.formatINR(value ?? "0", compact);
}

function parseDate(value) {
  const [year, month, day] = String(value).split("-").map(Number);
  return new Date(year, month - 1, day, 12);
}

function displayDate(value, full = false) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("en-US", full ? { month: "short", day: "numeric", year: "numeric" } : { month: "short", day: "numeric" }).format(parseDate(value));
}

function monthKey(value) { return String(value).slice(0, 7); }
function currentMonthKey() { return todayISO.slice(0, 7); }
function byDateNewest(a, b) { return b.date.localeCompare(a.date) || b.id.localeCompare(a.id); }
function expenseSum(items) { return FinanceDecimal.sum(items.filter((item) => item.type === "EXPENSE").map((item) => item.amount)); }
function incomeSum(items) { return FinanceDecimal.sum(items.filter((item) => item.type === "INCOME").map((item) => item.amount)); }
function thisMonthItems() { return transactions.filter((item) => monthKey(item.date) === currentMonthKey()); }
function itemsForMonth(month) { return transactions.filter((item) => monthKey(item.date) === month); }
function categoryTotals(items) {
  const totals = new Map(CATEGORIES.map((category) => [category, "0"]));
  items.filter((item) => item.type === "EXPENSE").forEach((item) => totals.set(item.category, FinanceDecimal.add(totals.get(item.category) || "0", item.amount)));
  return [...totals.entries()].filter(([, amount]) => FinanceDecimal.compare(amount, "0") > 0)
    .sort((a, b) => FinanceDecimal.compare(b[1], a[1]));
}

function categoryChip(category) {
  const style = CATEGORY_STYLE[category] || CATEGORY_STYLE.Other;
  return `<span class="category-chip"><i style="background:${style.color}"></i>${escapeHtml(category)}</span>`;
}

function transactionName(item) {
  const style = CATEGORY_STYLE[item.category] || CATEGORY_STYLE.Other;
  return `<div class="transaction-name"><span class="transaction-glyph ${item.type === "INCOME" ? "income" : ""}" style="--glyph-color:${style.color}">${style.icon}</span><span class="transaction-title"><strong>${escapeHtml(item.description)}</strong><small>${escapeHtml(item.category)}</small></span></div>`;
}

function transactionRows(items, withActions = false) {
  if (!items.length) return "";
  return items.map((item) => `<tr>
    <td>${transactionName(item)}</td>
    ${withActions ? `<td><span class="type-pill ${item.type === "EXPENSE" ? "expense" : ""}">${item.type === "INCOME" ? "Income" : "Expense"}</span></td>` : ""}
    <td>${categoryChip(item.category)}</td><td>${displayDate(item.date, withActions)}</td>
    <td class="align-right ${item.type === "INCOME" ? "amount-positive" : "amount-negative"}">${item.type === "INCOME" ? "+" : "−"}${money(item.amount)}</td>
    ${withActions ? `<td class="actions-cell"><div class="row-actions"><button class="row-action edit" data-edit="${escapeHtml(item.id)}" aria-label="Edit ${escapeHtml(item.description)}" title="Edit"><svg viewBox="0 0 24 24"><path d="m14 5 5 5M4 20l4.4-1 10.8-10.8a2.1 2.1 0 0 0-3-3L5.4 16 4 20Z"/></svg></button><button class="row-action delete" data-delete="${escapeHtml(item.id)}" aria-label="Delete ${escapeHtml(item.description)}" title="Delete"><svg viewBox="0 0 24 24"><path d="M4 7h16M10 11v6m4-6v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg></button></div></td>` : ""}
  </tr>`).join("");
}

function refreshDashboard() {
  const monthItems = thisMonthItems();
  const income = incomeSum(transactions);
  const expenses = expenseSum(transactions);
  const monthIncome = incomeSum(monthItems);
  const monthExpenses = expenseSum(monthItems);
  document.getElementById("total-balance").textContent = money(FinanceDecimal.subtract(income, expenses));
  document.getElementById("total-income").textContent = money(income);
  document.getElementById("total-expenses").textContent = money(expenses);
  document.getElementById("monthly-income-summary").textContent = `This month ${money(monthIncome)}`;
  document.getElementById("monthly-expense-summary").textContent = `This month ${money(monthExpenses)}`;
  renderDashboardBudget(monthExpenses);
  const recentTransactions = [...transactions].sort(byDateNewest).slice(0, 5);
  document.getElementById("recent-table").innerHTML = recentTransactions.length
    ? transactionRows(recentTransactions)
    : `<tr><td colspan="4" class="table-empty-cell">${transactionsLoaded ? "No transactions yet. Add one to get started." : "Transactions are not available until the Java server connects."}</td></tr>`;
  document.getElementById("transaction-count").textContent = String(transactions.length);
  drawCashflowChart("cashflow-chart", "bar");
  drawCategoryChart("category-chart", "category-legend", "category-total", monthItems);
}

function readTransactionFilters() {
  return {
    type: document.getElementById("type-filter").value,
    category: document.getElementById("category-filter").value,
    startDate: document.getElementById("date-from").value,
    endDate: document.getElementById("date-to").value,
    search: document.getElementById("search-input").value.trim()
  };
}

function hasActiveFilters(filters) {
  return Object.values(filters).some((value) => value && value !== "all");
}

async function refreshTransactions() {
  const requestNumber = ++filterRequestNumber;
  if (!transactionsLoaded) {
    document.getElementById("transaction-summary").textContent = "Waiting for the local Java server…";
    document.getElementById("transactions-table").innerHTML = "";
    document.getElementById("transactions-empty").classList.remove("hidden");
    document.querySelector("#transactions-empty strong").textContent = "Transactions are not loaded";
    document.querySelector("#transactions-empty p").textContent = "Connect to the local Java server and retry.";
    document.querySelector("#view-transactions .table-scroll").classList.add("hidden");
    return;
  }

  const filters = readTransactionFilters();
  let filtered = transactions;
  if (hasActiveFilters(filters)) {
    document.getElementById("transaction-summary").textContent = "Filtering transactions…";
    setConnectionStatus("loading", "Filtering transactions through the local Java API…");
    try {
      filtered = await PersonalExpenseApi.listTransactions(filters);
      if (!Array.isArray(filtered)) throw new Error("The Java server returned an invalid transaction list.");
    } catch (error) {
      if (requestNumber === filterRequestNumber) {
        document.getElementById("transaction-summary").textContent = "Filter request failed; showing previous results.";
        setConnectionStatus("error", error.message);
      }
      return;
    }
    if (requestNumber === filterRequestNumber) setConnectionStatus("hidden");
  }
  if (requestNumber !== filterRequestNumber) return;
  filtered = [...filtered].sort(byDateNewest);
  document.getElementById("transactions-table").innerHTML = transactionRows(filtered, true);
  document.getElementById("transaction-summary").textContent = `${filtered.length} ${filtered.length === 1 ? "transaction" : "transactions"}`;
  document.getElementById("transactions-empty").classList.toggle("hidden", filtered.length > 0);
  document.querySelector("#view-transactions .table-scroll").classList.toggle("hidden", filtered.length === 0);
  if (filtered.length === 0) {
    const noData = transactions.length === 0 && !hasActiveFilters(filters);
    document.querySelector("#transactions-empty strong").textContent = noData ? "No transactions yet" : "No transactions found";
    document.querySelector("#transactions-empty p").textContent = noData
      ? "Add your first transaction to see it here."
      : "Try adjusting your search or filters.";
  }
  if (!hasActiveFilters(filters)) setConnectionStatus("hidden");
}

function renderDashboardBudget(monthExpenses) {
  const summary = budgetSummaries.get(currentMonthKey());
  const summaryAvailable = summary !== undefined;
  const hasBudget = summary?.budgetAmount !== null && summary?.budgetAmount !== undefined;
  const amount = summary?.budgetAmount ?? "0";
  const spent = summary?.totalExpenses ?? monthExpenses;
  const remaining = summary?.remainingBudget ?? "0";
  const usage = summary?.usagePercentage ?? FinanceDecimal.percentOf(spent, amount);
  document.getElementById("budget-total").textContent = !budgetsLoaded ? "Loading…" : !summaryAvailable ? "Unavailable" : hasBudget ? money(amount) : "Not set";
  const progress = document.getElementById("budget-progress");
  progress.style.width = `${FinanceDecimal.compare(usage, "100") >= 0 ? 100 : Math.max(0, FinanceDecimal.visualNumber(usage))}%`;
  progress.classList.toggle("over", Boolean(summary?.exceeded));
  document.getElementById("budget-caption").textContent = !budgetsLoaded
    ? "Loading current month…"
    : !summaryAvailable ? "Budget details are unavailable"
    : hasBudget
      ? `${FinanceDecimal.formatPercent(FinanceDecimal.round(usage, 0), 0)}% used · ${money(FinanceDecimal.absolute(remaining))} ${summary?.exceeded ? "over budget" : "left"}`
      : `${money(spent)} spent · Set a monthly budget`;
}

function monthTitle(month) {
  const [year, monthNumber] = month.split("-").map(Number);
  return MONTH_LABEL.format(new Date(year, monthNumber - 1, 1));
}

function refreshBudget() {
  const monthItems = itemsForMonth(selectedBudgetMonth);
  const summary = budgetSummaries.get(selectedBudgetMonth);
  const summaryAvailable = summary !== undefined;
  const hasBudget = summary?.budgetAmount !== null && summary?.budgetAmount !== undefined;
  const spent = summary?.totalExpenses ?? expenseSum(monthItems);
  const limit = hasBudget ? summary.budgetAmount : "0";
  const remaining = hasBudget ? summary.remainingBudget : null;
  const usage = hasBudget ? (summary.usagePercentage ?? FinanceDecimal.percentOf(spent, limit)) : "0";
  document.getElementById("budget-month-title").textContent = monthTitle(selectedBudgetMonth);
  document.getElementById("budget-month-select").value = selectedBudgetMonth;
  document.getElementById("budget-spent-large").textContent = money(spent);
  document.getElementById("budget-limit-large").textContent = !budgetsLoaded ? "Loading…" : !summaryAvailable ? "Unavailable" : hasBudget ? money(limit) : "Not set";
  document.getElementById("budget-empty-state").classList.toggle("hidden", !budgetsLoaded || !summaryAvailable || hasBudget);
  document.getElementById("budget-remaining-large").textContent = hasBudget ? `${money(FinanceDecimal.absolute(remaining))} ${summary?.exceeded ? "over budget" : "remaining"}` : "—";
  document.getElementById("budget-spent-caption").textContent = !budgetsLoaded
    ? "Loading budget details…"
    : !summaryAvailable ? "Budget details are unavailable" : hasBudget ? `${FinanceDecimal.formatPercent(FinanceDecimal.round(usage, 0), 0)}% of your budget used` : `${money(spent)} spent this month · no limit set`;
  const bar = document.getElementById("budget-large-progress");
  bar.style.width = `${FinanceDecimal.compare(usage, "100") >= 0 ? 100 : Math.max(0, FinanceDecimal.visualNumber(usage))}%`;
  bar.classList.toggle("over", Boolean(summary?.exceeded));
  document.getElementById("budget-warning").classList.toggle("hidden", !summary?.exceeded);
  document.getElementById("budget-warning-copy").textContent = summary?.exceeded ? `You are ${money(FinanceDecimal.absolute(remaining))} over the monthly limit.` : "Your monthly spending is within the limit.";
  document.getElementById("budget-side-title").textContent = hasBudget ? "Small steps add up." : "Build a plan that feels right.";
  document.getElementById("budget-side-copy").textContent = hasBudget
    ? summary?.exceeded ? `Spending is ${money(FinanceDecimal.absolute(remaining))} above your plan for ${monthTitle(selectedBudgetMonth)}.` : `You have ${money(remaining)} of planned spending left for ${monthTitle(selectedBudgetMonth)}.`
    : "Set a monthly limit and keep an eye on what matters to you.";
  document.getElementById("budget-cta").innerHTML = `${hasBudget ? "Adjust this budget" : "Create this month's budget"} <span>→</span>`;
  const categoryList = categoryTotals(monthItems);
  document.getElementById("budget-category-list").innerHTML = categoryList.length ? categoryList.map(([name, amount]) => {
    const style = CATEGORY_STYLE[name] || CATEGORY_STYLE.Other;
    const portion = FinanceDecimal.percentOf(amount, spent);
    const portionLabel = FinanceDecimal.formatPercent(FinanceDecimal.round(portion, 0), 0);
    const width = FinanceDecimal.compare(portion, "100") >= 0 ? 100 : Math.max(0, FinanceDecimal.visualNumber(portion));
    return `<article class="budget-category-card"><div class="budget-category-head"><span class="budget-category-icon" style="color:${style.color}">${style.icon}</span><span class="budget-category-copy"><strong>${escapeHtml(name)}</strong><span>${portionLabel}% of this month’s expenses</span></span><span class="budget-category-amount">${money(amount)}<small>spent</small></span></div><div class="budget-category-track"><span style="width:${width}%; background:${style.color}"></span></div></article>`;
  }).join("") : `<article class="budget-category-card"><div class="budget-category-copy"><strong>No expenses yet this month</strong><span>Add a transaction to see your category breakdown.</span></div></article>`;
  renderSavedBudgets();
}

function renderSavedBudgets() {
  const container = document.getElementById("saved-budgets-list");
  if (!budgetsLoaded) {
    container.innerHTML = `<article class="budget-category-card"><div class="budget-category-copy"><strong>Loading saved budgets…</strong><span>Reading monthly budgets from the Java server.</span></div></article>`;
    return;
  }
  if (!budgets.length) {
    container.innerHTML = `<article class="budget-category-card"><div class="budget-category-copy"><strong>No saved monthly budgets</strong><span>Set a budget to see it in this list.</span></div></article>`;
    return;
  }
  container.innerHTML = [...budgets].sort((a, b) => b.month.localeCompare(a.month)).map((item) => {
    const amount = item.budgetAmount ?? "0";
    const spent = item.totalExpenses ?? "0";
    const usage = item.usagePercentage ?? "0";
    const left = item.remainingBudget ?? "0";
    const usageLabel = FinanceDecimal.formatPercent(FinanceDecimal.round(usage, 0), 0);
    const width = FinanceDecimal.compare(usage, "100") >= 0 ? 100 : Math.max(0, FinanceDecimal.visualNumber(usage));
    return `<article class="budget-category-card saved-budget-card"><div class="budget-category-head"><span class="budget-category-icon">◷</span><span class="budget-category-copy"><strong>${escapeHtml(monthTitle(item.month))}</strong><span>${money(spent)} spent of ${money(amount)}</span></span><span class="budget-category-amount">${item.exceeded ? `${money(FinanceDecimal.absolute(left))} over` : `${money(left)} left`}<small>${usageLabel}% used</small></span></div><div class="budget-category-track"><span class="${item.exceeded ? "over" : ""}" style="width:${width}%"></span></div><button class="text-button saved-budget-edit" type="button" data-edit-budget-month="${escapeHtml(item.month)}">View or edit <span>→</span></button></article>`;
  }).join("");
}

async function loadBudgetsFromApi(loadingMessage = "Loading budgets from the local Java server…") {
  const requestNumber = ++budgetRequestNumber;
  const wasLoaded = budgetsLoaded;
  setConnectionStatus("loading", loadingMessage);
  try {
    const loadedBudgets = await PersonalExpenseApi.listBudgets();
    if (!Array.isArray(loadedBudgets)) throw new Error("The Java server returned an invalid budget list.");
    const months = [...new Set([selectedBudgetMonth, currentMonthKey()])];
    const summaries = await Promise.all(months.map((month) => PersonalExpenseApi.getBudget(month)));
    if (requestNumber !== budgetRequestNumber) return false;
    budgets = loadedBudgets;
    budgetSummaries = new Map(loadedBudgets.map((item) => [item.month, item]));
    months.forEach((month, index) => budgetSummaries.set(month, summaries[index]));
    budgetsLoaded = true;
    refreshDashboard();
    refreshBudget();
    setConnectionStatus("hidden");
    return true;
  } catch (error) {
    if (requestNumber === budgetRequestNumber) {
      budgetsLoaded = wasLoaded;
      refreshDashboard();
      refreshBudget();
      setConnectionStatus("error", error.message);
    }
    return false;
  }
}

function setReportsStatus(state, message = "") {
  const status = document.getElementById("reports-status");
  status.className = state === "hidden" ? "reports-status hidden" : `reports-status ${state}`;
  status.textContent = message;
}

function updateReportControls() {
  const period = document.getElementById("report-period").value;
  document.getElementById("report-date-wrap").classList.toggle("hidden", period !== "day");
  document.getElementById("report-month-wrap").classList.toggle("hidden", period !== "month");
  document.getElementById("report-year-wrap").classList.toggle("hidden", period !== "year");
}

function setReportSummary(prefix, income, expenses, savings) {
  document.getElementById(`${prefix}-income`).textContent = money(income);
  document.getElementById(`${prefix}-expenses`).textContent = money(expenses);
  const savingsElement = document.getElementById(`${prefix}-savings`);
  const isNegative = FinanceDecimal.compare(savings, "0") < 0;
  savingsElement.textContent = `${isNegative ? "−" : ""}${money(FinanceDecimal.absolute(savings))}`;
  savingsElement.classList.toggle("negative", isNegative);
  const captionId = prefix === "report-period" ? "report-period-savings-caption" : null;
  if (captionId) document.getElementById(captionId).textContent = isNegative ? "Expenses exceed income" : "Income minus expenses";
}

async function loadReports() {
  const requestNumber = ++reportsRequestNumber;
  const period = document.getElementById("report-period").value;
  const date = document.getElementById("report-date").value;
  const month = document.getElementById("report-month").value;
  const year = document.getElementById("report-year").value;
  const categoryMonth = document.getElementById("report-category-month").value;
  const periodRequest = period === "day"
    ? (date ? PersonalExpenseApi.getDailyReport(date) : Promise.reject(new Error("Choose a date for the daily report.")))
    : period === "year"
      ? (/^\d{4}$/.test(year) && Number(year) > 0 ? PersonalExpenseApi.getYearlyReport(year) : Promise.reject(new Error("Enter a valid year from 0001 to 9999.")))
      : (month ? PersonalExpenseApi.getMonthlyReport(month) : Promise.reject(new Error("Choose a month for the monthly report.")));

  setReportsStatus("loading", "Loading reports from the Java server…");
  try {
    const [summary, periodReport, categoryReport] = await Promise.all([
      PersonalExpenseApi.getReportSummary(),
      periodRequest,
      categoryMonth ? PersonalExpenseApi.getCategoryReport(categoryMonth) : Promise.reject(new Error("Choose a month for category analytics."))
    ]);
    if (requestNumber !== reportsRequestNumber) return;
    renderReportSummary(summary);
    renderPeriodReport(period, periodReport);
    renderCategoryReport(categoryReport);
    setReportsStatus("hidden");
  } catch (error) {
    if (requestNumber === reportsRequestNumber) setReportsStatus("error", error.message || "Reports could not be loaded.");
  }
}

function renderReportSummary(summary) {
  document.getElementById("report-total-income").textContent = money(summary.totalIncome);
  document.getElementById("report-total-expenses").textContent = money(summary.totalExpenses);
  const savings = summary.netSavings ?? "0";
  const savingsElement = document.getElementById("report-total-savings");
  const isNegative = FinanceDecimal.compare(savings, "0") < 0;
  savingsElement.textContent = `${isNegative ? "−" : ""}${money(FinanceDecimal.absolute(savings))}`;
  savingsElement.classList.toggle("negative", isNegative);
  document.getElementById("report-total-transactions").textContent = Number(summary.totalTransactions).toLocaleString("en-IN");
}

function renderPeriodReport(period, report) {
  let title;
  let description;
  let income;
  let expenses;
  let savings;
  let labels;
  let chartIncome;
  let chartExpenses;
  let breakdown = [];

  if (period === "day") {
    title = `Daily report · ${displayDate(report.date, true)}`;
    description = `${Number(report.transactionCount) || 0} transactions on this date.`;
    income = report.dailyIncome;
    expenses = report.dailyExpenses;
    savings = report.dailySavings;
    labels = [displayDate(report.date, true)];
    chartIncome = [report.dailyIncome ?? "0"];
    chartExpenses = [report.dailyExpenses ?? "0"];
    document.getElementById("report-chart-subtitle").textContent = "Income and expenses for this day";
  } else if (period === "year") {
    title = `Yearly report · ${report.year}`;
    description = `${Number(report.transactionCount) || 0} transactions during ${report.year}.`;
    income = report.yearlyIncome;
    expenses = report.yearlyExpenses;
    savings = report.yearlySavings;
    breakdown = report.monthlyBreakdown || [];
    labels = breakdown.map((item) => new Intl.DateTimeFormat("en", { month: "short" }).format(parseDate(`${item.month}-01`)));
    chartIncome = breakdown.map((item) => item.monthlyIncome ?? "0");
    chartExpenses = breakdown.map((item) => item.monthlyExpenses ?? "0");
    document.getElementById("report-chart-subtitle").textContent = `Monthly income and expenses in ${report.year}`;
  } else {
    title = `Monthly report · ${monthTitle(report.month)}`;
    description = `${Number(report.transactionCount) || 0} transactions during ${monthTitle(report.month)}.`;
    income = report.monthlyIncome;
    expenses = report.monthlyExpenses;
    savings = report.monthlySavings;
    breakdown = report.monthlyComparison || [];
    labels = breakdown.map((item) => new Intl.DateTimeFormat("en", { month: "short" }).format(parseDate(`${item.month}-01`)));
    chartIncome = breakdown.map((item) => item.monthlyIncome ?? "0");
    chartExpenses = breakdown.map((item) => item.monthlyExpenses ?? "0");
    document.getElementById("report-chart-subtitle").textContent = `Monthly comparison for ${report.month.slice(0, 4)}`;
  }

  document.getElementById("report-period-title").textContent = title;
  document.getElementById("report-period-description").textContent = description;
  setReportSummary("report-period", income, expenses, savings);
  document.getElementById("yearly-breakdown-panel").classList.toggle("hidden", period !== "year");
  if (period === "year") renderYearlyBreakdown(breakdown);
  drawReportCashflowChart(labels, chartIncome, chartExpenses);
}

function renderYearlyBreakdown(months) {
  document.getElementById("yearly-breakdown-table").innerHTML = months.length ? months.map((item) => {
    const monthName = new Intl.DateTimeFormat("en", { month: "long" }).format(parseDate(`${item.month}-01`));
    const savings = item.monthlySavings ?? "0";
    const isNegative = FinanceDecimal.compare(savings, "0") < 0;
    return `<tr><td>${escapeHtml(monthName)}</td><td class="align-right amount-positive">${money(item.monthlyIncome)}</td><td class="align-right amount-negative">${money(item.monthlyExpenses)}</td><td class="align-right ${isNegative ? "amount-negative" : "amount-positive"}">${isNegative ? "−" : ""}${money(FinanceDecimal.absolute(savings))}</td></tr>`;
  }).join("") : `<tr><td colspan="4" class="table-empty-cell">No monthly data was returned for this year.</td></tr>`;
}

function renderCategoryReport(report) {
  const categories = report.categories || [];
  document.getElementById("report-category-subtitle").textContent = `Expenses in ${monthTitle(report.month)}`;
  document.getElementById("report-category-total").textContent = money(report.totalExpenses, true);
  document.getElementById("report-category-highest").textContent = report.highestSpendingCategory || "None";
  document.getElementById("report-category-lowest").textContent = report.lowestSpendingCategory || "None";
  drawReportCategoryChart(categories);
}

function drawReportCashflowChart(labels, income, expenses) {
  if (!window.Chart) return;
  const canvas = document.getElementById("report-cashflow-chart");
  if (!canvas) return;
  if (reportCashflowChart) reportCashflowChart.destroy();
  const options = chartDefaults();
  options.elements = { line: { tension: .34, borderWidth: 2 }, point: { radius: 2, hoverRadius: 4, borderWidth: 0 } };
  reportCashflowChart = new Chart(canvas, {
    type: "line",
    data: { labels, datasets: [
      { label: "Income", data: income, backgroundColor: "rgba(87,214,160,.08)", borderColor: "#57d6a0", borderWidth: 2, fill: true },
      { label: "Expenses", data: expenses, backgroundColor: "rgba(231,131,130,.04)", borderColor: "#db8985", borderWidth: 2, fill: true }
    ] },
    options
  });
}

function drawReportCategoryChart(categories) {
  if (!window.Chart) return;
  const canvas = document.getElementById("report-category-chart");
  if (!canvas) return;
  if (reportCategoryChart) reportCategoryChart.destroy();
  const labels = categories.map((item) => item.category);
  const amounts = categories.map((item) => item.totalExpenses ?? "0");
  const colors = labels.map((category) => CATEGORY_STYLE[category]?.color || CATEGORY_STYLE.Other.color);
  reportCategoryChart = new Chart(canvas, {
    type: "doughnut",
    data: { labels, datasets: [{ data: amounts, backgroundColor: colors, borderColor: "#11181d", borderWidth: 3, hoverOffset: 4 }] },
    options: {
      responsive: true, maintainAspectRatio: false, cutout: "76%",
      plugins: { legend: { display: false }, tooltip: { enabled: categories.length > 0, backgroundColor: "#202a2f", borderColor: "#3a474b", borderWidth: 1, titleColor: "#edf3f0", bodyColor: "#bac6c2", callbacks: { label: (context) => {
        const category = categories[context.dataIndex];
        return ` ${context.label}: ${money(category.totalExpenses)} (${FinanceDecimal.formatPercent(category.percentage)}%)`;
      } } } }
    }
  });
  document.getElementById("report-category-legend").innerHTML = categories.length
    ? categories.map((item, index) => `<div class="category-legend-item"><i style="background:${colors[index]}"></i><span>${escapeHtml(item.category)} · ${FinanceDecimal.formatPercent(item.percentage)}%</span><strong>${money(item.totalExpenses, true)}</strong></div>`).join("")
    : `<div class="category-legend-item"><span>No expenses in this month</span></div>`;
}

function chartDefaults() {
  return {
    responsive: true, maintainAspectRatio: false,
    interaction: { intersect: false, mode: "index" },
    plugins: {
      legend: { display: false },
      tooltip: { backgroundColor: "#202a2f", borderColor: "#3a474b", borderWidth: 1, titleColor: "#edf3f0", bodyColor: "#bac6c2", padding: 10, displayColors: true, boxPadding: 4,
        callbacks: { label: (context) => ` ${context.dataset.label}: ${money(context.dataset.data[context.dataIndex])}` } }
    },
    scales: {
      x: { grid: { display: false }, border: { display: false }, ticks: { color: "#758286", font: { family: "-apple-system, BlinkMacSystemFont, Segoe UI, sans-serif", size: 9 } } },
      y: { beginAtZero: true, border: { display: false }, grid: { color: "rgba(137,151,154,.12)", drawTicks: false }, ticks: { color: "#657377", padding: 9, font: { family: "-apple-system, BlinkMacSystemFont, Segoe UI, sans-serif", size: 8 }, callback: (value) => money(value, true) } }
    }
  };
}

function cashflowByMonth(count = 6, yearOnly = false) {
  const labels = [];
  const income = [];
  const expenses = [];
  if (yearOnly) {
    for (let month = 0; month <= today.getMonth(); month += 1) {
      const date = new Date(today.getFullYear(), month, 1);
      const key = toISODate(date).slice(0, 7);
      const items = transactions.filter((item) => monthKey(item.date) === key);
      labels.push(new Intl.DateTimeFormat("en", { month: "short" }).format(date));
      income.push(incomeSum(items));
      expenses.push(expenseSum(items));
    }
  } else {
    for (let offset = count - 1; offset >= 0; offset -= 1) {
      const date = new Date(today.getFullYear(), today.getMonth() - offset, 1);
      const key = toISODate(date).slice(0, 7);
      const items = transactions.filter((item) => monthKey(item.date) === key);
      labels.push(new Intl.DateTimeFormat("en", { month: "short" }).format(date));
      income.push(incomeSum(items));
      expenses.push(expenseSum(items));
    }
  }
  return { labels, income, expenses };
}

function drawCashflowChart(id, type, period = "month") {
  if (!window.Chart) return;
  const canvas = document.getElementById(id);
  if (!canvas) return;
  const variable = id === "cashflow-chart" ? "cashflowChart" : "reportCashflowChart";
  if (window[variable]) window[variable].destroy();
  const instance = variable === "cashflowChart" ? cashflowChart : reportCashflowChart;
  if (instance) instance.destroy();
  const data = cashflowByMonth(6, period === "year");
  if (period === "day") {
    const dayItems = transactions.filter((item) => item.date === todayISO);
    data.labels = ["Today"];
    data.income = [incomeSum(dayItems)];
    data.expenses = [expenseSum(dayItems)];
  }
  const options = chartDefaults();
  if (type === "bar") {
    options.scales.x.stacked = false;
    options.datasets = { bar: { maxBarThickness: 18, borderRadius: 4, borderSkipped: false } };
  } else {
    options.elements = { line: { tension: .34, borderWidth: 2 }, point: { radius: 2, hoverRadius: 4, borderWidth: 0 } };
  }
  const chart = new Chart(canvas, {
    type,
    data: { labels: data.labels, datasets: [
      { label: "Income", data: data.income, backgroundColor: type === "bar" ? "rgba(87,214,160,.78)" : "rgba(87,214,160,.08)", borderColor: "#57d6a0", borderWidth: type === "bar" ? 0 : 2, fill: type !== "bar" },
      { label: "Expenses", data: data.expenses, backgroundColor: type === "bar" ? "rgba(231,131,130,.69)" : "rgba(231,131,130,.04)", borderColor: "#db8985", borderWidth: type === "bar" ? 0 : 2, fill: type !== "bar" }
    ] }, options
  });
  if (variable === "cashflowChart") cashflowChart = chart;
  else reportCashflowChart = chart;
}

function drawCategoryChart(canvasId, legendId, totalId, items) {
  if (!window.Chart) return;
  const totals = categoryTotals(items);
  const labels = totals.length ? totals.map(([name]) => name) : ["No expenses"];
  const values = totals.length ? totals.map(([, amount]) => amount) : ["1"];
  const colors = totals.length ? totals.map(([name]) => CATEGORY_STYLE[name]?.color || CATEGORY_STYLE.Other.color) : ["#273237"];
  const canvas = document.getElementById(canvasId);
  const key = canvasId === "category-chart" ? "categoryChart" : "reportCategoryChart";
  const old = key === "categoryChart" ? categoryChart : reportCategoryChart;
  if (old) old.destroy();
  const chart = new Chart(canvas, { type: "doughnut", data: { labels, datasets: [{ data: values, backgroundColor: colors, borderColor: "#11181d", borderWidth: 3, hoverOffset: 4 }] }, options: {
    responsive: true, maintainAspectRatio: false, cutout: "76%", plugins: { legend: { display: false }, tooltip: { enabled: totals.length > 0, backgroundColor: "#202a2f", borderColor: "#3a474b", borderWidth: 1, titleColor: "#edf3f0", bodyColor: "#bac6c2", callbacks: { label: (context) => ` ${context.label}: ${money(totals[context.dataIndex]?.[1] ?? "0")}` } } }
  } });
  if (key === "categoryChart") categoryChart = chart;
  else reportCategoryChart = chart;
  document.getElementById(totalId).textContent = money(expenseSum(items), true);
  const legend = document.getElementById(legendId);
  legend.innerHTML = totals.length ? totals.slice(0, 6).map(([name, amount]) => `<div class="category-legend-item"><i style="background:${CATEGORY_STYLE[name]?.color || CATEGORY_STYLE.Other.color}"></i><span>${escapeHtml(name)}</span><strong>${money(amount, true)}</strong></div>`).join("") : `<div class="category-legend-item"><span>No expenses yet</span></div>`;
}

function refreshAll() {
  refreshDashboard();
  refreshTransactions();
  refreshBudget();
  loadReports();
}

function showView(view) {
  const safeView = ["dashboard", "transactions", "budgets", "reports"].includes(view) ? view : "dashboard";
  document.querySelectorAll(".view").forEach((element) => element.classList.toggle("active", element.id === `view-${safeView}`));
  document.querySelectorAll(".nav-link").forEach((link) => link.classList.toggle("active", link.dataset.view === safeView));
  const labels = { dashboard: "Overview", transactions: "Transactions", budgets: "Budgets", reports: "Reports" };
  document.getElementById("page-crumb").textContent = labels[safeView];
  document.title = `${labels[safeView]} · Northstar`;
  document.getElementById("sidebar").classList.remove("open");
  if (safeView === "reports") loadReports();
}

function openTransactionModal(item = null) {
  const modal = document.getElementById("transaction-modal");
  const form = document.getElementById("transaction-form");
  form.reset();
  document.getElementById("transaction-id").value = item?.id || "";
  document.getElementById("transaction-type").value = item?.type || "EXPENSE";
  document.getElementById("transaction-amount").value = item?.amount ?? "";
  document.getElementById("transaction-category").value = item?.category || "Food";
  document.getElementById("transaction-date").value = item?.date || todayISO;
  document.getElementById("transaction-description").value = item?.description || "";
  document.querySelectorAll(".type-option").forEach((button) => button.classList.toggle("selected", button.dataset.type === (item?.type || "EXPENSE")));
  document.getElementById("transaction-modal-title").textContent = item ? "Edit transaction" : "Add transaction";
  document.getElementById("save-transaction").textContent = item ? "Save changes" : "Save transaction";
  modal.classList.add("open");
  modal.setAttribute("aria-hidden", "false");
  setTimeout(() => document.getElementById("transaction-amount").focus(), 30);
}

async function saveTransactionRecord(record) {
  const saveButton = document.getElementById("save-transaction");
  const isUpdate = Boolean(record.id);
  saveButton.disabled = true;
  setConnectionStatus("loading", isUpdate ? "Saving transaction changes…" : "Adding transaction…");
  try {
    const saved = isUpdate
      ? await PersonalExpenseApi.updateTransaction(record.id, record)
      : await PersonalExpenseApi.createTransaction(record);

    if (isUpdate) {
      transactions = transactions.map((item) => item.id === saved.id ? saved : item);
    } else {
      transactions = [...transactions, saved];
    }
    transactionsLoaded = true;
    closeModal("transaction-modal");
    refreshAll();
    const refreshed = await loadTransactionsFromApi("Refreshing the dashboard…");
    const budgetsRefreshed = await loadBudgetsFromApi("Refreshing budget progress…");
    showToast(refreshed
      ? (budgetsRefreshed ? (isUpdate ? "Transaction updated" : "Transaction added") : "Transaction saved; budget progress could not refresh.")
      : "Saved on the Java server; the dashboard could not refresh.");
  } catch (error) {
    setConnectionStatus("error", error.message);
    showToast(error.message);
  } finally {
    saveButton.disabled = false;
  }
}

async function removeTransaction(id) {
  const item = transactions.find((transaction) => transaction.id === id);
  if (!item || !window.confirm(`Delete “${item.description}”? This cannot be undone.`)) return;

  setConnectionStatus("loading", "Deleting transaction…");
  try {
    await PersonalExpenseApi.deleteTransaction(id);
    transactions = transactions.filter((transaction) => transaction.id !== id);
    transactionsLoaded = true;
    refreshAll();
    const refreshed = await loadTransactionsFromApi("Refreshing the dashboard…");
    const budgetsRefreshed = await loadBudgetsFromApi("Refreshing budget progress…");
    showToast(refreshed ? (budgetsRefreshed ? "Transaction deleted" : "Deleted; budget progress could not refresh.") : "Deleted on the Java server; the dashboard could not refresh.");
  } catch (error) {
    setConnectionStatus("error", error.message);
    showToast(error.message);
  }
}

function closeModal(id) {
  const modal = document.getElementById(id);
  modal.classList.remove("open");
  modal.setAttribute("aria-hidden", "true");
}

function openBudgetModal(month = selectedBudgetMonth) {
  const summary = budgetSummaries.get(month);
  document.getElementById("budget-form-month").value = month;
  document.getElementById("budget-amount-input").value = summary?.budgetAmount ?? "";
  document.getElementById("budget-modal-title").textContent = summary?.budgetAmount == null ? "Set your budget" : "Edit your budget";
  document.getElementById("budget-modal").classList.add("open");
  document.getElementById("budget-modal").setAttribute("aria-hidden", "false");
  setTimeout(() => document.getElementById("budget-amount-input").focus(), 30);
}

function showToast(message) {
  const toast = document.getElementById("toast");
  toast.textContent = message;
  toast.classList.add("show");
  clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => toast.classList.remove("show"), 2600);
}

function setConnectionStatus(state, message = "") {
  const banner = document.getElementById("connection-banner");
  const retryButton = document.getElementById("connection-retry");
  if (state === "hidden") {
    banner.className = "connection-banner hidden";
    return;
  }
  banner.className = `connection-banner ${state}`;
  document.getElementById("connection-message").textContent = message;
  retryButton.classList.toggle("hidden", state !== "error");
}

async function loadTransactionsFromApi(loadingMessage = "Loading transactions from the local Java server…") {
  const wasLoaded = transactionsLoaded;
  setConnectionStatus("loading", loadingMessage);
  try {
    const loadedTransactions = await PersonalExpenseApi.listTransactions();
    if (!Array.isArray(loadedTransactions)) {
      throw new Error("The Java server returned an invalid transaction list.");
    }
    transactions = loadedTransactions;
    transactionsLoaded = true;
    refreshAll();
    setConnectionStatus("hidden");
    return true;
  } catch (error) {
    // Keep already loaded (or just-mutated) values on screen if a refresh temporarily fails.
    transactionsLoaded = wasLoaded;
    if (!wasLoaded) {
      transactions = [];
      refreshAll();
    }
    setConnectionStatus("error", error.message);
    return false;
  }
}

function initializeCategoryOptions() {
  const transactionSelect = document.getElementById("transaction-category");
  const filterSelect = document.getElementById("category-filter");
  transactionSelect.innerHTML = CATEGORIES.map((category) => `<option>${escapeHtml(category)}</option>`).join("");
  filterSelect.insertAdjacentHTML("beforeend", CATEGORIES.map((category) => `<option value="${escapeHtml(category)}">${escapeHtml(category)}</option>`).join(""));
}

function setHeaderDate() {
  document.getElementById("today-label").textContent = new Intl.DateTimeFormat("en-US", { weekday: "short", month: "short", day: "numeric" }).format(today);
  const hour = today.getHours();
  document.getElementById("greeting").textContent = hour < 12 ? "YOUR FINANCES, AT A GLANCE · GOOD MORNING" : hour < 18 ? "YOUR FINANCES, AT A GLANCE · GOOD AFTERNOON" : "YOUR FINANCES, AT A GLANCE · GOOD EVENING";
}

function initializeApplication() {
  initializeCategoryOptions();
  setHeaderDate();
  document.getElementById("report-date").value = todayISO;
  document.getElementById("report-month").value = currentMonthKey();
  document.getElementById("report-year").value = String(today.getFullYear());
  document.getElementById("report-category-month").value = currentMonthKey();
  updateReportControls();
  refreshAll();
  showView(location.hash.slice(1) || "dashboard");
  document.getElementById("connection-retry").addEventListener("click", async () => {
    const transactionsOk = await loadTransactionsFromApi();
    const budgetsOk = await loadBudgetsFromApi();
    if (!transactionsOk || !budgetsOk) setConnectionStatus("error", "The local Java server could not load all application data. Check the server and retry.");
  });
  (async () => {
    const transactionsOk = await loadTransactionsFromApi();
    const budgetsOk = await loadBudgetsFromApi();
    if (!transactionsOk || !budgetsOk) setConnectionStatus("error", "The local Java server could not load all application data. Check the server and retry.");
  })();

  document.querySelectorAll("[data-view]").forEach((link) => link.addEventListener("click", (event) => {
    event.preventDefault();
    const view = link.dataset.view;
    if (location.hash !== `#${view}`) location.hash = view;
    showView(view);
  }));
  document.querySelectorAll("[data-nav]").forEach((button) => button.addEventListener("click", () => {
    const view = button.dataset.nav;
    location.hash = view;
    showView(view);
  }));
  window.addEventListener("hashchange", () => showView(location.hash.slice(1)));
  document.querySelectorAll("[data-open-transaction]").forEach((button) => button.addEventListener("click", () => openTransactionModal()));
  document.querySelectorAll(".close-modal").forEach((button) => button.addEventListener("click", () => closeModal("transaction-modal")));
  document.querySelectorAll(".close-budget-modal").forEach((button) => button.addEventListener("click", () => closeModal("budget-modal")));
  document.querySelectorAll(".overlay").forEach((overlay) => overlay.addEventListener("click", (event) => { if (event.target === overlay) closeModal(overlay.id); }));
  document.addEventListener("keydown", (event) => { if (event.key === "Escape") { closeModal("transaction-modal"); closeModal("budget-modal"); document.getElementById("sidebar").classList.remove("open"); } });
  document.querySelectorAll(".type-option").forEach((button) => button.addEventListener("click", () => {
    document.getElementById("transaction-type").value = button.dataset.type;
    document.querySelectorAll(".type-option").forEach((option) => option.classList.toggle("selected", option === button));
  }));
  document.getElementById("transaction-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const amount = document.getElementById("transaction-amount").value.trim();
    if (!amount || FinanceDecimal.compare(amount, "0") <= 0) return;
    const id = document.getElementById("transaction-id").value;
    const record = { type: document.getElementById("transaction-type").value, amount, category: document.getElementById("transaction-category").value, date: document.getElementById("transaction-date").value, description: document.getElementById("transaction-description").value.trim() };
    if (!record.date || !record.description) return;
    if (id) record.id = id;
    await saveTransactionRecord(record);
  });
  document.getElementById("transactions-table").addEventListener("click", (event) => {
    const editButton = event.target.closest("[data-edit]");
    const deleteButton = event.target.closest("[data-delete]");
    if (editButton) {
      const item = transactions.find((transaction) => transaction.id === editButton.dataset.edit);
      if (item) openTransactionModal(item);
    }
    if (deleteButton) {
      removeTransaction(deleteButton.dataset.delete);
    }
  });
  document.getElementById("search-input").addEventListener("input", () => {
    clearTimeout(filterTimeout);
    filterTimeout = setTimeout(() => refreshTransactions(), 250);
  });
  ["type-filter", "category-filter", "date-from", "date-to"].forEach((id) => document.getElementById(id).addEventListener("change", refreshTransactions));
  document.getElementById("clear-filters").addEventListener("click", () => {
    ["search-input", "date-from", "date-to"].forEach((id) => { document.getElementById(id).value = ""; });
    document.getElementById("type-filter").value = "all";
    document.getElementById("category-filter").value = "all";
    refreshTransactions();
  });
  document.getElementById("edit-budget-button").addEventListener("click", openBudgetModal);
  document.getElementById("budget-cta").addEventListener("click", openBudgetModal);
  document.getElementById("budget-month-select").addEventListener("change", (event) => selectBudgetMonth(event.target.value));
  document.getElementById("saved-budgets-list").addEventListener("click", (event) => {
    const button = event.target.closest("[data-edit-budget-month]");
    if (!button) return;
    selectBudgetMonth(button.dataset.editBudgetMonth).then(() => openBudgetModal(button.dataset.editBudgetMonth));
  });
  document.getElementById("budget-form-month").addEventListener("change", async (event) => {
    const month = event.target.value;
    if (!month) return;
    if (!budgetSummaries.has(month)) {
      try { budgetSummaries.set(month, await PersonalExpenseApi.getBudget(month)); }
      catch (error) { setConnectionStatus("error", error.message); }
    }
    const summary = budgetSummaries.get(month);
    document.getElementById("budget-amount-input").value = summary?.budgetAmount ?? "";
    document.getElementById("budget-modal-title").textContent = summary?.budgetAmount == null ? "Set your budget" : "Edit your budget";
  });
  document.getElementById("budget-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const amount = document.getElementById("budget-amount-input").value.trim();
    if (!amount || FinanceDecimal.compare(amount, "0") <= 0) return;
    const month = document.getElementById("budget-form-month").value;
    if (!/^\d{4}-\d{2}$/.test(month)) return;
    const saveButton = document.getElementById("save-budget-button");
    saveButton.disabled = true;
    setConnectionStatus("loading", "Saving monthly budget to the Java server…");
    try {
      const saved = await PersonalExpenseApi.saveBudget(month, amount);
      budgetSummaries.set(month, saved);
      selectedBudgetMonth = month;
      const refreshed = await loadBudgetsFromApi("Refreshing saved budgets…");
      closeModal("budget-modal");
      showToast(refreshed ? "Monthly budget saved" : "Budget saved, but the list could not refresh.");
    } catch (error) {
      setConnectionStatus("error", error.message);
      showToast(error.message);
    } finally {
      saveButton.disabled = false;
    }
  });
  document.getElementById("report-period").addEventListener("change", () => {
    const period = document.getElementById("report-period").value;
    const categoryMonth = document.getElementById("report-category-month");
    if (period === "month") categoryMonth.value = document.getElementById("report-month").value;
    if (period === "day") categoryMonth.value = document.getElementById("report-date").value.slice(0, 7);
    if (period === "year") categoryMonth.value = `${document.getElementById("report-year").value}-${categoryMonth.value.slice(5, 7) || "01"}`;
    updateReportControls();
    loadReports();
  });
  document.getElementById("report-date").addEventListener("change", (event) => {
    document.getElementById("report-category-month").value = event.target.value.slice(0, 7);
    loadReports();
  });
  document.getElementById("report-month").addEventListener("change", (event) => {
    document.getElementById("report-category-month").value = event.target.value;
    loadReports();
  });
  document.getElementById("report-year").addEventListener("change", (event) => {
    const categoryMonth = document.getElementById("report-category-month");
    categoryMonth.value = `${event.target.value}-${categoryMonth.value.slice(5, 7) || "01"}`;
    loadReports();
  });
  document.getElementById("report-category-month").addEventListener("change", loadReports);
  document.getElementById("menu-toggle").addEventListener("click", () => document.getElementById("sidebar").classList.toggle("open"));
  document.querySelectorAll(".sidebar .nav-link").forEach((link) => link.addEventListener("click", () => document.getElementById("sidebar").classList.remove("open")));
  document.getElementById("logout-button").addEventListener("click", signOut);
}

function setAuthFeedback(message = "", success = false) {
  const feedback = document.getElementById("auth-feedback");
  feedback.textContent = message;
  feedback.classList.toggle("hidden", !message);
  feedback.classList.toggle("success", Boolean(message) && success);
  feedback.setAttribute("role", success ? "status" : "alert");
}

function setAuthMode(mode) {
  const loginMode = mode === "login";
  document.getElementById("login-form").classList.toggle("hidden", !loginMode);
  document.getElementById("register-form").classList.toggle("hidden", loginMode);
  document.getElementById("show-login").classList.toggle("selected", loginMode);
  document.getElementById("show-login").setAttribute("aria-selected", String(loginMode));
  document.getElementById("show-register").classList.toggle("selected", !loginMode);
  document.getElementById("show-register").setAttribute("aria-selected", String(!loginMode));
  document.getElementById("auth-title").textContent = loginMode ? "Welcome back" : "Create your account";
  setAuthFeedback("");
}

function showAuthScreen(message = "", success = false) {
  document.getElementById("app-shell").classList.add("hidden");
  document.getElementById("auth-screen").classList.remove("hidden");
  setAuthFeedback(message, success);
}

function clearFinancialState() {
  transactions = [];
  transactionsLoaded = false;
  budgets = [];
  budgetSummaries.clear();
  budgetsLoaded = false;
  [cashflowChart, categoryChart, reportCashflowChart, reportCategoryChart].forEach((chart) => chart?.destroy());
  cashflowChart = categoryChart = reportCashflowChart = reportCategoryChart = undefined;
}

async function activateAccount(user) {
  authenticatedUser = user;
  const shortName = user.email.split("@")[0];
  document.getElementById("profile-email").textContent = user.email;
  document.getElementById("profile-avatar").textContent = shortName.slice(0, 1).toUpperCase();
  document.getElementById("header-avatar").textContent = shortName.slice(0, 1).toUpperCase();
  document.getElementById("app-shell").classList.remove("hidden");
  document.getElementById("auth-screen").classList.add("hidden");
  if (!appInitialized) {
    appInitialized = true;
    initializeApplication();
    return;
  }
  showView(location.hash.slice(1) || "dashboard");
  const transactionsOk = await loadTransactionsFromApi();
  const budgetsOk = await loadBudgetsFromApi();
  if (!transactionsOk || !budgetsOk) setConnectionStatus("error", "Some account data could not be loaded. Retry when the server is available.");
}

async function signOut() {
  const button = document.getElementById("logout-button");
  button.disabled = true;
  try {
    await PersonalExpenseApi.logout();
    clearFinancialState();
    authenticatedUser = null;
    showAuthScreen("You have signed out.", true);
  } catch (error) {
    showToast(error.message);
  } finally {
    button.disabled = false;
  }
}

function initializeAuthentication() {
  document.getElementById("show-login").addEventListener("click", () => setAuthMode("login"));
  document.getElementById("show-register").addEventListener("click", () => setAuthMode("register"));
  document.getElementById("login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = document.getElementById("login-submit");
    const data = new FormData(form);
    button.disabled = true;
    setAuthFeedback("");
    try {
      await PersonalExpenseApi.login(data.get("email"), data.get("password"));
      await activateAccount(await PersonalExpenseApi.currentUser());
    } catch (error) {
      setAuthFeedback(error.message);
    } finally {
      button.disabled = false;
    }
  });
  document.getElementById("register-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const button = document.getElementById("register-submit");
    const data = new FormData(form);
    button.disabled = true;
    setAuthFeedback("");
    try {
      const user = await PersonalExpenseApi.register(data.get("email"), data.get("password"));
      document.querySelector("#login-form [name=email]").value = user.email;
      form.reset();
      setAuthMode("login");
      setAuthFeedback("Account created. Sign in to continue.", true);
    } catch (error) {
      setAuthFeedback(error.message);
    } finally {
      button.disabled = false;
    }
  });
  window.addEventListener("expense-session-expired", () => {
    if (!authenticatedUser) return;
    clearFinancialState();
    authenticatedUser = null;
    showAuthScreen("Your session expired. Sign in again.");
  });
  showAuthScreen();
  (async () => {
    try {
      await PersonalExpenseApi.refreshCsrfToken();
      await activateAccount(await PersonalExpenseApi.currentUser());
    } catch (error) {
      if (error.status === 401) showAuthScreen();
      else showAuthScreen(error.message);
    }
  })();
}

document.addEventListener("DOMContentLoaded", initializeAuthentication);
