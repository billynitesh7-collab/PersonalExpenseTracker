/* Centralized browser client for the local Java APIs. */
(function () {
  const TRANSACTIONS_URL = "/api/transactions";
  const BUDGETS_URL = "/api/budgets";
  const REPORTS_URL = "/api/reports";
  const AUTH_URL = "/api/auth";
  let csrfToken = null;
  let csrfHeaderName = "X-CSRF-TOKEN";

  // JSON.parse normally turns decimal JSON numbers into binary floating-point values.
  // Quote numeric tokens first so Java money values arrive as their exact decimal text.
  function parseResponseJson(text) {
    let output = "";
    for (let index = 0; index < text.length;) {
      const character = text[index];
      if (character === '"') {
        const start = index++;
        while (index < text.length) {
          if (text[index] === "\\") {
            index += 2;
          } else if (text[index++] === '"') {
            break;
          }
        }
        output += text.slice(start, index);
        continue;
      }
      if (character === "-" || (character >= "0" && character <= "9")) {
        const match = text.slice(index).match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/);
        if (match) {
          // Preserve the token's original scale and exponent as well as its numeric value.
          output += JSON.stringify(match[0]);
          index += match[0].length;
          continue;
        }
      }
      output += character;
      index += 1;
    }
    return JSON.parse(output);
  }

  function jsonWithDecimalField(value, fieldName) {
    const decimalText = FinanceDecimal.normalize(value[fieldName]);
    const copy = { ...value, [fieldName]: decimalText };
    const serialized = JSON.stringify(copy);
    const field = `${JSON.stringify(fieldName)}:${JSON.stringify(decimalText)}`;
    return serialized.replace(field, `${JSON.stringify(fieldName)}:${decimalText}`);
  }

  async function refreshCsrfToken() {
    let response;
    try {
      response = await fetch(`${AUTH_URL}/csrf`, { credentials: "same-origin", cache: "no-store" });
    } catch (error) {
      throw new Error("Could not reach the Java server at http://localhost:8081.");
    }
    const result = parseResponseJson(await response.text());
    if (!response.ok || !result?.token) throw new Error(result?.error || "Could not initialize request protection.");
    csrfToken = result.token;
    csrfHeaderName = result.headerName || "X-CSRF-TOKEN";
    return csrfToken;
  }

  async function request(path, options = {}) {
    const method = (options.method || "GET").toUpperCase();
    const headers = {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers
    };
    if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
      if (!csrfToken) await refreshCsrfToken();
      headers[csrfHeaderName] = csrfToken;
    }
    let response;
    try {
      response = await fetch(path, {
        ...options,
        method,
        credentials: "same-origin",
        headers
      });
    } catch (error) {
      throw new Error("Could not reach the Java server at http://localhost:8081.");
    }

    const responseText = await response.text();
    let result = null;
    if (responseText) {
      try {
        result = parseResponseJson(responseText);
      } catch (error) {
        if (!response.ok) {
          throw new Error(`The Java API returned HTTP ${response.status}. Restart the application with the current backend and retry.`);
        }
        throw new Error("The Java server returned a response the app could not read.");
      }
    }

    if (!response.ok) {
      const error = new Error(result?.error || `The request failed (${response.status}).`);
      error.status = response.status;
      if (response.status === 401 && !path.startsWith(`${AUTH_URL}/login`) && !path.startsWith(`${AUTH_URL}/register`) && !path.startsWith(`${AUTH_URL}/me`)) {
        window.dispatchEvent(new Event("expense-session-expired"));
      }
      throw error;
    }
    return result;
  }

  function listTransactions(filters = {}) {
    const query = new URLSearchParams();
    Object.entries(filters).forEach(([name, value]) => {
      if (value !== undefined && value !== null && value !== "" && value !== "all") {
        query.set(name, value);
      }
    });
    const suffix = query.toString();
    return request(suffix ? `${TRANSACTIONS_URL}?${suffix}` : TRANSACTIONS_URL);
  }

  function listBudgets() {
    return request(BUDGETS_URL);
  }

  function getBudget(month) {
    const query = new URLSearchParams({ month });
    return request(`${BUDGETS_URL}?${query}`);
  }

  function getReport(path, parameter, value) {
    const query = new URLSearchParams({ [parameter]: value });
    return request(`${REPORTS_URL}/${path}?${query}`);
  }

  window.PersonalExpenseApi = {
    refreshCsrfToken,
    currentUser() { return request(`${AUTH_URL}/me`); },
    register(email, password) {
      return request(`${AUTH_URL}/register`, { method: "POST", body: JSON.stringify({ email, password }) });
    },
    async login(email, password) {
      const user = await request(`${AUTH_URL}/login`, { method: "POST", body: JSON.stringify({ email, password }) });
      csrfToken = null;
      await refreshCsrfToken();
      return user;
    },
    async logout() {
      await request(`${AUTH_URL}/logout`, { method: "POST" });
      csrfToken = null;
      await refreshCsrfToken();
    },
    listTransactions,
    listBudgets,
    getBudget,
    saveBudget(month, amount) {
      return request(`${BUDGETS_URL}/${encodeURIComponent(month)}`, {
        method: "PUT",
        body: jsonWithDecimalField({ amount }, "amount")
      });
    },
    getReportSummary() {
      return request(`${REPORTS_URL}/summary`);
    },
    getDailyReport(date) {
      return getReport("daily", "date", date);
    },
    getMonthlyReport(month) {
      return getReport("monthly", "month", month);
    },
    getYearlyReport(year) {
      return getReport("yearly", "year", year);
    },
    getCategoryReport(month) {
      return getReport("categories", "month", month);
    },
    createTransaction(transaction) {
      return request(TRANSACTIONS_URL, { method: "POST", body: jsonWithDecimalField(transaction, "amount") });
    },
    updateTransaction(id, transaction) {
      return request(`${TRANSACTIONS_URL}/${encodeURIComponent(id)}`, {
        method: "PUT",
        body: jsonWithDecimalField(transaction, "amount")
      });
    },
    deleteTransaction(id) {
      return request(`${TRANSACTIONS_URL}/${encodeURIComponent(id)}`, { method: "DELETE" });
    }
  };
})();
