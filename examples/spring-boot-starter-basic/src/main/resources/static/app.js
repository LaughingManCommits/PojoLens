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
    statsOptions: {},
    chartOptions: {}
};
const endpoints = {
    dashboard: "/api/employees/dashboard",
    dashboardOptions: "/api/employees/dashboard-options",
    departments: "/api/employees/departments",
    employees: "/api/employees",
    topPaid: "/api/employees/top-paid"
};

function money(value) {
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        maximumFractionDigits: 0
    }).format(value);
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
    const context = document.getElementById(canvasId).getContext("2d");
    if (charts[key]) {
        charts[key].destroy();
    }
    charts[key] = new Chart(context, payload);
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
    document.getElementById("statsTitle").textContent = stats.mode;
    document.getElementById("statsSource").textContent = `${stats.title}\n${stats.source}`;

    const head = document.getElementById("statsTableHead");
    const body = document.getElementById("statsTableBody");
    head.innerHTML = `<tr>${stats.columns.map((column) => `<th>${escapeHtml(column)}</th>`).join("")}</tr>`;
    if (!stats.rows.length) {
        body.innerHTML = emptyRow(Math.max(stats.columns.length, 1), "No rows returned for this stats mode.");
    } else {
        body.innerHTML = stats.rows.map((row) => `
            <tr>${stats.columns.map((column) => `<td>${escapeHtml(pretty(row[column]))}</td>`).join("")}</tr>
        `).join("");
    }

    const totals = document.getElementById("statsTotals");
    const entries = Object.entries(stats.totals || {});
    if (entries.length === 0) {
        totals.innerHTML = '<span class="text-body-secondary">No totals for this mode.</span>';
        return;
    }
    totals.innerHTML = entries.map(([key, value]) => `
        <span class="badge text-bg-light border me-2 mb-2">${escapeHtml(key)}: ${escapeHtml(pretty(value))}</span>
    `).join("");
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

function renderModeHelp(containerId, option) {
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

function renderModeOptions(options, selectedStatsMode, selectedChartMode) {
    state.statsOptions = indexOptions(options.statsModeDetails);
    state.chartOptions = indexOptions(options.chartModeDetails);
    renderSelect("statsMode", options.statsModes || [], selectedStatsMode);
    renderSelect("chartMode", options.chartModes || [], selectedChartMode);
    renderModeHelp("statsModeHelp", state.statsOptions[document.getElementById("statsMode").value]);
    renderModeHelp("chartModeHelp", state.chartOptions[document.getElementById("chartMode").value]);
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

async function refreshDashboard(selectedModes) {
    const statsMode = selectedModes?.statsMode || document.getElementById("statsMode").value;
    const chartMode = selectedModes?.chartMode || document.getElementById("chartMode").value;
    const query = new URLSearchParams({statsMode: statsMode, chartMode: chartMode});
    const dashboard = await fetchJson(`${endpoints.dashboard}?${query.toString()}`);
    renderEmployees(dashboard.employees);
    renderStats(dashboard.stats);
    renderRuntime(dashboard.runtime);
    renderModeOptions(dashboard.options, dashboard.selectedStatsMode, dashboard.selectedChartMode);
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

document.getElementById("statsMode").addEventListener("change", () => {
    renderModeHelp("statsModeHelp", state.statsOptions[document.getElementById("statsMode").value]);
});

document.getElementById("chartMode").addEventListener("change", () => {
    renderModeHelp("chartModeHelp", state.chartOptions[document.getElementById("chartMode").value]);
});

document.getElementById("presetForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
        await refreshDashboard();
    } catch (error) {
        showFeedback(`Preset refresh failed: ${error.message}`, true);
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

async function boot() {
    try {
        const options = await fetchJson(endpoints.dashboardOptions);
        renderModeOptions(options, options.defaultStatsMode, options.defaultChartMode);
        await refreshDepartments();
        await refreshDashboard({
            statsMode: options.defaultStatsMode,
            chartMode: options.defaultChartMode
        });
        await refreshTopPaid();
    } catch (error) {
        showFeedback(`Dashboard bootstrap failed: ${error.message}`, true);
    }
}

boot();
