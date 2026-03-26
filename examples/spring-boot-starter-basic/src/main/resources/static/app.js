/*
 * Frontend companion for the starter dashboard.
 *
 * Read next:
 * - examples/spring-boot-starter-basic/README.md
 * - /docs/stats-presets.md
 * - /docs/charts.md
 * - /docs/reports.md
 */
const charts = window.charts = {payroll: null, headcount: null};
const state = {
    statsViews: {},
    chartTypes: {}
};
const endpoints = {
    dashboard: "/api/employees/dashboard",
    dashboardOptions: "/api/employees/dashboard-options",
    departments: "/api/employees/departments",
    employees: "/api/employees",
    topPaid: "/api/employees/top-paid"
};

function clientErrorPanel() {
    return document.getElementById("clientErrorPanel");
}

function clientErrorText() {
    return document.getElementById("clientErrorText");
}

function clearClientError() {
    const panel = clientErrorPanel();
    panel.dataset.hasError = "false";
    panel.classList.add("d-none");
    clientErrorText().textContent = "";
}

function formatClientError(error) {
    if (error instanceof Error) {
        return `${error.name}: ${error.message}`;
    }
    if (typeof error === "string") {
        return error;
    }
    try {
        return JSON.stringify(error);
    } catch (jsonError) {
        return `${error}`;
    }
}

function reportClientError(stage, error) {
    const panel = clientErrorPanel();
    const text = clientErrorText();
    const message = `[${stage}] ${formatClientError(error)}`;
    const existing = text.textContent.trim();
    text.textContent = existing ? `${existing}\n${message}` : message;
    panel.dataset.hasError = "true";
    panel.classList.remove("d-none");
    return message;
}

function money(value) {
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        maximumFractionDigits: 0
    }).format(value);
}

function count(value) {
    return Number(value || 0).toLocaleString("en-US");
}

function pretty(value) {
    if (typeof value === "number") {
        if (Math.abs(value) >= 1000) {
            return money(value);
        }
        return value.toLocaleString();
    }
    return `${value}`;
}

function escapeHtml(value) {
    return `${value}`
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function showFeedback(message, isError) {
    const container = document.getElementById("feedback");
    const cls = isError ? "alert-danger" : "alert-success";
    container.innerHTML = `<div class="alert ${cls} py-2 mb-0">${escapeHtml(message)}</div>`;
    window.setTimeout(() => {
        container.innerHTML = "";
    }, 3000);
}

async function errorMessage(response) {
    const text = await response.text();
    if (!text) {
        return `HTTP ${response.status}`;
    }
    try {
        const payload = JSON.parse(text);
        return payload.message || payload.error || text;
    } catch (error) {
        return text;
    }
}

async function fetchJson(url, options) {
    const opts = options || {};
    const headers = Object.assign({Accept: "application/json"}, opts.headers || {});
    if (opts.body !== undefined && headers["Content-Type"] === undefined && headers["content-type"] === undefined) {
        headers["Content-Type"] = "application/json";
    }
    const response = await fetch(url, Object.assign({}, opts, {headers}));
    if (!response.ok) {
        throw new Error(await errorMessage(response));
    }
    if (response.status === 204) {
        return null;
    }
    return response.json();
}

function emptyRow(columnCount, label) {
    return `<tr><td class="table-empty text-center py-4" colspan="${columnCount}">${escapeHtml(label)}</td></tr>`;
}

function renderChart(key, canvasId, payload) {
    try {
        const context = document.getElementById(canvasId).getContext("2d");
        if (charts[key]) {
            charts[key].destroy();
        }
        charts[key] = new Chart(context, payload);
    } catch (error) {
        reportClientError(`renderChart:${key}`, error);
        throw error;
    }
}

function renderEmployees(rows) {
    const table = document.getElementById("employeeTable");
    if (!rows.length) {
        table.innerHTML = emptyRow(4, "No employees yet.");
        return;
    }
    table.innerHTML = rows.map((employee) => `
        <tr>
            <td>${employee.id}</td>
            <td>${escapeHtml(employee.name)}</td>
            <td>${escapeHtml(employee.department)}</td>
            <td class="text-end">${escapeHtml(money(employee.salary))}</td>
        </tr>
    `).join("");
}

function renderTopPaid(rows) {
    const table = document.getElementById("topPaidTable");
    if (!rows.length) {
        table.innerHTML = emptyRow(4, "No employees match the current query.");
        return;
    }
    table.innerHTML = rows.map((employee, index) => `
        <tr>
            <td>${index + 1}</td>
            <td>${escapeHtml(employee.name)}</td>
            <td>${escapeHtml(employee.department)}</td>
            <td class="text-end">${escapeHtml(money(employee.salary))}</td>
        </tr>
    `).join("");
}

function renderStats(stats) {
    const statsDisplayTitle = document.getElementById("statsDisplayTitle");
    if (statsDisplayTitle) {
        statsDisplayTitle.textContent = stats.title;
    }
    document.getElementById("statsTitle").textContent = stats.view;
    document.getElementById("statsSource").textContent = `View: ${stats.title}\nSource: ${stats.source}`;

    const head = document.getElementById("statsTableHead");
    const body = document.getElementById("statsTableBody");
    head.innerHTML = `<tr>${stats.columns.map((column) => `<th>${escapeHtml(column)}</th>`).join("")}</tr>`;
    if (!stats.rows.length) {
        body.innerHTML = emptyRow(Math.max(stats.columns.length, 1), "No rows returned for this stats view.");
    } else {
        body.innerHTML = stats.rows.map((row) => `
            <tr>${stats.columns.map((column) => `<td>${escapeHtml(pretty(row[column]))}</td>`).join("")}</tr>
        `).join("");
    }

    const totals = document.getElementById("statsTotals");
    const entries = Object.entries(stats.totals || {});
    if (entries.length === 0) {
        totals.innerHTML = '<span class="text-body-secondary">No totals for this view.</span>';
        return;
    }
    totals.innerHTML = entries.map(([key, value]) => `
        <span class="badge text-bg-light border me-2 mb-2">${escapeHtml(key)}: ${escapeHtml(pretty(value))}</span>
    `).join("");
}

function renderOverview(employees, selectedStatsView, selectedChartType) {
    const departmentCount = new Set(employees.map((employee) => employee.department)).size;
    const payroll = employees.reduce((total, employee) => total + Number(employee.salary || 0), 0);
    const averageSalary = employees.length === 0 ? 0 : payroll / employees.length;
    const statsOption = state.statsViews[selectedStatsView];
    const chartOption = state.chartTypes[selectedChartType];

    document.getElementById("overviewStatsView").textContent =
        statsOption ? statsOption.label : selectedStatsView;
    document.getElementById("overviewChartType").textContent =
        chartOption ? chartOption.label : selectedChartType;
    document.getElementById("overviewEmployees").textContent = count(employees.length);
    document.getElementById("overviewDepartments").textContent = count(departmentCount);
    document.getElementById("overviewPayroll").textContent = money(payroll);
    document.getElementById("overviewAverageSalary").textContent = money(averageSalary);
}

function renderRuntime(runtime) {
    document.getElementById("strictFlag").textContent = `strict: ${runtime.strictParameterTypes}`;
    document.getElementById("lintFlag").textContent = `lint: ${runtime.lintMode}`;
    document.getElementById("cacheFlag").textContent =
        `cache: sql=${runtime.sqlLikeCacheEnabled}, stats=${runtime.statsPlanCacheEnabled}`;
}

function indexOptions(options) {
    const indexed = {};
    (options || []).forEach((option) => {
        indexed[option.value] = option;
    });
    return indexed;
}

function renderSelect(selectId, values, selectedValue) {
    const select = document.getElementById(selectId);
    const currentValue = selectedValue || select.value;
    select.innerHTML = values
        .map((value) => `<option value="${escapeHtml(value)}">${escapeHtml(value)}</option>`)
        .join("");
    if (values.includes(currentValue)) {
        select.value = currentValue;
    } else if (values.length > 0) {
        select.value = values[0];
    }
}

function renderOptionHelp(containerId, option) {
    const container = document.getElementById(containerId);
    if (!option) {
        container.innerHTML = "";
        return;
    }
    container.innerHTML = `
        <div class="d-flex justify-content-between align-items-start gap-3 mb-2">
            <div class="mode-label">${escapeHtml(option.label)}</div>
            <span class="badge text-bg-light border">${escapeHtml(option.value)}</span>
        </div>
        <div class="mode-summary mb-2">${escapeHtml(option.summary)}</div>
        <div class="mode-doc text-body-secondary">Read more: <code>${escapeHtml(option.docPath)}</code></div>
    `;
}

function renderDashboardOptions(options, selectedStatsView, selectedChartType) {
    state.statsViews = indexOptions(options.statsViewDetails);
    state.chartTypes = indexOptions(options.chartTypeDetails);
    renderSelect("statsView", options.statsViews || [], selectedStatsView);
    renderSelect("chartType", options.chartTypes || [], selectedChartType);
    renderOptionHelp("statsViewHelp", state.statsViews[document.getElementById("statsView").value]);
    renderOptionHelp("chartTypeHelp", state.chartTypes[document.getElementById("chartType").value]);
}

function renderDepartmentOptions(departments) {
    const topPaidDepartment = document.getElementById("topPaidDepartment");
    const previousDepartment = topPaidDepartment.value;
    topPaidDepartment.innerHTML = departments
        .map((department) => `<option value="${escapeHtml(department)}">${escapeHtml(department)}</option>`)
        .join("");
    if (departments.includes(previousDepartment)) {
        topPaidDepartment.value = previousDepartment;
    } else if (departments.length > 0) {
        topPaidDepartment.value = departments[0];
    }

    const datalist = document.getElementById("departmentOptions");
    datalist.innerHTML = departments
        .map((department) => `<option value="${escapeHtml(department)}"></option>`)
        .join("");
}

async function refreshDepartments() {
    renderDepartmentOptions(await fetchJson(endpoints.departments));
}

async function refreshDashboard(selectedOptions) {
    clearClientError();
    const statsView = selectedOptions?.statsView || document.getElementById("statsView").value;
    const chartType = selectedOptions?.chartType || document.getElementById("chartType").value;
    const query = new URLSearchParams({statsView: statsView, chartType: chartType});
    const dashboard = await fetchJson(`${endpoints.dashboard}?${query.toString()}`);
    renderEmployees(dashboard.employees);
    renderRuntime(dashboard.runtime);
    renderDashboardOptions(dashboard.options, dashboard.selectedStatsView, dashboard.selectedChartType);
    renderOverview(dashboard.employees, dashboard.selectedStatsView, dashboard.selectedChartType);
    renderStats(dashboard.stats);
    renderChart("payroll", "payrollChart", dashboard.payrollChart);
    renderChart("headcount", "headcountChart", dashboard.headcountChart);
}

async function refreshTopPaid() {
    const query = new URLSearchParams({
        department: document.getElementById("topPaidDepartment").value,
        minSalary: document.getElementById("topPaidMinSalary").value,
        limit: document.getElementById("topPaidLimit").value
    });
    renderTopPaid(await fetchJson(`${endpoints.topPaid}?${query.toString()}`));
}

document.getElementById("statsView").addEventListener("change", () => {
    renderOptionHelp("statsViewHelp", state.statsViews[document.getElementById("statsView").value]);
});

document.getElementById("chartType").addEventListener("change", () => {
    renderOptionHelp("chartTypeHelp", state.chartTypes[document.getElementById("chartType").value]);
});

document.getElementById("dashboardForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
        await refreshDashboard();
    } catch (error) {
        showFeedback(`Dashboard refresh failed: ${error.message}`, true);
    }
});

document.getElementById("addEmployeeForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const payload = {
        name: document.getElementById("employeeName").value,
        department: document.getElementById("employeeDepartment").value,
        salary: Number(document.getElementById("employeeSalary").value)
    };
    try {
        await fetchJson(endpoints.employees, {
            method: "POST",
            body: JSON.stringify(payload)
        });
        event.target.reset();
        await refreshDepartments();
        await refreshDashboard();
        await refreshTopPaid();
        showFeedback("Employee added.", false);
    } catch (error) {
        showFeedback(`Add employee failed: ${error.message}`, true);
    }
});

document.getElementById("topPaidForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
        await refreshTopPaid();
    } catch (error) {
        showFeedback(`Top-paid query failed: ${error.message}`, true);
    }
});

window.addEventListener("error", (event) => {
    reportClientError("window.error", event.error || event.message || "Unknown window error");
});

window.addEventListener("unhandledrejection", (event) => {
    reportClientError("window.unhandledrejection", event.reason || "Unhandled promise rejection");
});

async function boot() {
    try {
        clearClientError();
        const options = await fetchJson(endpoints.dashboardOptions);
        renderDashboardOptions(options, options.defaultStatsView, options.defaultChartType);
        await refreshDepartments();
        await refreshDashboard({
            statsView: options.defaultStatsView,
            chartType: options.defaultChartType
        });
        await refreshTopPaid();
    } catch (error) {
        showFeedback(`Dashboard bootstrap failed: ${error.message}`, true);
    }
}

boot();
