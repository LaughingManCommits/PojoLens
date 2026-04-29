const state = {
  range: "30d",
  region: "ALL",
  status: "ALL",
  riskBand: "ALL",
  segment: "ALL",
  search: "",
  limit: 25,
  transactionPageIndex: 0,
  transactionPageCursors: [null],
  transactionNextCursor: null,
  bootstrap: null,
  selectedMerchantId: null,
  selectedReportId: null,
  reportCatalog: [],
  sortBy: "id",
  sortDirection: "desc",
  activeTab: "overviewTab",
  exploreSubTab: "workbenchSubTab",
  reportsSubTab: "reportOutputSubTab"
};

const charts = {};
const TOP_LEVEL_TABS = ["overviewTab", "analyticsTab", "operationsTab", "exploreTab", "reportsTab"];
const SUBTAB_GROUPS = {
  explore: {
    stateKey: "exploreSubTab",
    urlKey: "pojoLens",
    tabs: ["workbenchSubTab", "queryStudioSubTab"]
  },
  reports: {
    stateKey: "reportsSubTab",
    urlKey: "reports",
    tabs: ["reportOutputSubTab", "reportInspectorSubTab"]
  }
};

document.addEventListener("DOMContentLoaded", async () => {
  hydrateNavigationStateFromUrl();
  bindFilters();
  bindTabs();
  bindSubTabs();
  window.addEventListener("hashchange", () => {
    hydrateNavigationStateFromUrl();
    syncNavigationState({ persistUrl: false });
  });
  await loadBootstrap();
  await loadReportCatalog();
  await loadDashboard(true);
  syncNavigationState({ persistUrl: false });
});

function bindFilters() {
  [
    ["rangeFilter", "range"],
    ["regionFilter", "region"],
    ["statusFilter", "status"],
    ["riskBandFilter", "riskBand"],
    ["segmentFilter", "segment"],
    ["transactionSortField", "sortBy"],
    ["transactionSortDirection", "sortDirection"]
  ].forEach(([id, key]) => {
    document.getElementById(id).addEventListener("change", async (event) => {
      state[key] = event.target.value;
      resetTransactionPaging();
      await loadDashboard(true);
    });
  });

  document.getElementById("searchFilter").addEventListener("input", debounce(async (event) => {
    state.search = event.target.value.trim();
    resetTransactionPaging();
    await loadDashboard(true);
  }, 250));

  document.getElementById("nextPageButton").addEventListener("click", async () => {
    if (!state.transactionNextCursor) {
      return;
    }
    if (state.transactionPageIndex === state.transactionPageCursors.length - 1) {
      state.transactionPageCursors.push(state.transactionNextCursor);
    } else {
      state.transactionPageCursors[state.transactionPageIndex + 1] = state.transactionNextCursor;
    }
    state.transactionPageIndex += 1;
    await loadTransactions();
  });

  document.getElementById("previousPageButton").addEventListener("click", async () => {
    if (state.transactionPageIndex === 0) {
      return;
    }
    state.transactionPageIndex -= 1;
    await loadTransactions();
  });

  document.getElementById("reportSelect").addEventListener("change", async (event) => {
    state.selectedReportId = event.target.value;
    renderReportMeta();
    await Promise.all([runSelectedReport(), inspectSelectedReport()]);
  });

  document.getElementById("runReportButton").addEventListener("click", async () => {
    activateSubTab("reports", "reportOutputSubTab");
    await runSelectedReport();
  });

  document.getElementById("inspectReportButton").addEventListener("click", async () => {
    activateSubTab("reports", "reportInspectorSubTab");
    await inspectSelectedReport();
  });

  document.getElementById("transactionDrawerClose").addEventListener("click", closeTransactionDrawer);
  document.getElementById("transactionDrawerBackdrop").addEventListener("click", closeTransactionDrawer);
}

function bindTabs() {
  document.querySelectorAll("[data-tab-target]").forEach((button) => {
    button.addEventListener("click", () => activateTab(button.dataset.tabTarget));
  });
  bindRovingTabNavigation(".tab-button", (button) => activateTab(button.dataset.tabTarget));
}

function bindSubTabs() {
  document.querySelectorAll("[data-subtab-target]").forEach((button) => {
    button.addEventListener("click", () => activateSubTab(button.dataset.subtabGroup, button.dataset.subtabTarget));
  });
  Object.keys(SUBTAB_GROUPS).forEach((groupName) => {
    bindRovingTabNavigation(`.subtab-button[data-subtab-group='${groupName}']`, (button) =>
      activateSubTab(groupName, button.dataset.subtabTarget)
    );
  });
}

function bindRovingTabNavigation(selector, activate) {
  const buttons = Array.from(document.querySelectorAll(selector));
  buttons.forEach((button, index) => {
    button.addEventListener("keydown", (event) => {
      let nextIndex = null;
      if (event.key === "ArrowRight" || event.key === "ArrowDown") {
        nextIndex = (index + 1) % buttons.length;
      } else if (event.key === "ArrowLeft" || event.key === "ArrowUp") {
        nextIndex = (index - 1 + buttons.length) % buttons.length;
      } else if (event.key === "Home") {
        nextIndex = 0;
      } else if (event.key === "End") {
        nextIndex = buttons.length - 1;
      } else if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        activate(button);
        return;
      } else {
        return;
      }
      event.preventDefault();
      buttons[nextIndex].focus();
    });
  });
}

async function loadBootstrap() {
  const payload = await fetchJson("/api/bootstrap");
  state.bootstrap = payload;
  state.range = payload.defaultRange;
  state.limit = payload.defaultLimit;
  fillSelect("rangeFilter", payload.ranges, payload.defaultRange, false);
  fillSelect("regionFilter", payload.regions, "ALL", true);
  fillSelect("statusFilter", payload.statuses, "ALL", true);
  fillSelect("riskBandFilter", payload.riskBands, "ALL", true);
  fillSelect("segmentFilter", payload.segments, "ALL", true);
  document.getElementById("transactionSortField").value = state.sortBy;
  document.getElementById("transactionSortDirection").value = state.sortDirection;
}

async function loadReportCatalog() {
  state.reportCatalog = await fetchJson("/api/reports");
  if (!state.selectedReportId && state.reportCatalog.length > 0) {
    state.selectedReportId = state.reportCatalog[0].id;
  }
  fillReportSelect();
  renderReportMeta();
}

async function loadDashboard(resetTransactions) {
  setStatus("Loading");
  clearError();
  try {
    const [summary, valueStory, trends, merchants, reviewQueue, workbench, queryStudio] = await Promise.all([
      fetchJson(`/api/dashboard/summary?${queryString()}`),
      fetchJson(`/api/dashboard/value-story?${queryString()}`),
      fetchJson(`/api/dashboard/trends?${queryString()}`),
      fetchJson(`/api/dashboard/top-merchants?${queryString()}`),
      fetchJson(`/api/reviews/queue?${reviewQueueQueryString()}`),
      fetchJson(`/api/dashboard/workbench?${reviewQueueQueryString()}`),
      fetchJson(`/api/dashboard/query-studio?${queryString()}`),
    ]);
    renderSummary(summary);
    renderValueStory(valueStory);
    renderTrends(trends);
    renderTopMerchants(merchants);
    renderReviewQueue(reviewQueue);
    renderWorkbench(workbench);
    renderQueryStudio(queryStudio);
    if (!state.selectedMerchantId && merchants.rows.length > 0) {
      state.selectedMerchantId = merchants.rows[0].merchantId;
    }
    if (state.selectedMerchantId) {
      await loadMerchantOverview();
    }
    if (resetTransactions) {
      resetTransactionPaging();
    }
    await Promise.all([
      loadTransactions(),
      runSelectedReport(),
      inspectSelectedReport()
    ]);
    syncNavigationState();
    setStatus("Live");
  } catch (error) {
    renderError(error);
    setStatus("Error");
  }
}

function activateTab(tabId, options = {}) {
  if (!TOP_LEVEL_TABS.includes(tabId)) {
    return;
  }
  state.activeTab = tabId;
  syncNavigationState(options);
}

function activateSubTab(groupName, tabId, options = {}) {
  const config = SUBTAB_GROUPS[groupName];
  if (!config || !config.tabs.includes(tabId)) {
    return;
  }
  state[config.stateKey] = tabId;
  syncNavigationState(options);
}

function syncNavigationState({ persistUrl = true } = {}) {
  document.querySelectorAll("[data-tab-panel]").forEach((panel) => {
    const isActive = panel.id === state.activeTab;
    panel.hidden = !isActive;
    panel.classList.toggle("active", isActive);
  });
  document.querySelectorAll(".tab-button").forEach((button) => {
    const isActive = button.dataset.tabTarget === state.activeTab;
    button.classList.toggle("active", isActive);
    button.setAttribute("aria-selected", String(isActive));
    button.tabIndex = isActive ? 0 : -1;
  });
  document.querySelectorAll(".sidebar-nav .nav-link").forEach((button) => {
    const isActive = button.dataset.tabTarget === state.activeTab;
    button.classList.toggle("active", isActive);
    button.setAttribute("aria-current", isActive ? "page" : "false");
  });
  Object.entries(SUBTAB_GROUPS).forEach(([groupName, config]) => {
    const activeSubTab = state[config.stateKey];
    document.querySelectorAll(`[data-subtab-panel][data-subtab-group='${groupName}']`).forEach((panel) => {
      const isActive = panel.id === activeSubTab;
      panel.hidden = !isActive;
      panel.classList.toggle("active", isActive);
    });
    document.querySelectorAll(`.subtab-button[data-subtab-group='${groupName}']`).forEach((button) => {
      const isActive = button.dataset.subtabTarget === activeSubTab;
      button.classList.toggle("active", isActive);
      button.setAttribute("aria-selected", String(isActive));
      button.tabIndex = isActive ? 0 : -1;
    });
  });
  if (persistUrl) {
    syncNavigationUrl();
  }
  resizeChartsSoon();
}

async function loadTransactions() {
  const url = new URL("/api/transactions", window.location.origin);
  currentParams().forEach((value, key) => url.searchParams.set(key, value));
  const currentCursor = state.transactionPageCursors[state.transactionPageIndex];
  if (currentCursor) {
    url.searchParams.set("cursor", currentCursor);
  }

  const payload = await fetchJson(url.toString());
  renderTransactions(payload);
}

async function loadMerchantOverview() {
  const payload = await fetchJson(`/api/merchants/${encodeURIComponent(state.selectedMerchantId)}/overview?range=${encodeURIComponent(state.range)}`);
  renderMerchantOverview(payload);
}

async function runSelectedReport() {
  if (!state.selectedReportId) {
    return;
  }
  const payload = await fetchJson(`/api/reports/${encodeURIComponent(state.selectedReportId)}/run?${queryString()}`);
  renderReportRun(payload);
}

async function inspectSelectedReport() {
  if (!state.selectedReportId) {
    return;
  }
  const payload = await fetchJson(`/api/reports/${encodeURIComponent(state.selectedReportId)}/inspect?${queryString()}`);
  renderReportInspect(payload);
}

function renderSummary(payload) {
  document.getElementById("rangeLabel").textContent = payload.rangeLabel;
  const host = document.getElementById("summaryCards");
  host.innerHTML = "";
  payload.cards.forEach((card) => {
    const el = document.createElement("article");
    el.className = `summary-card ${card.tone}`;
    el.innerHTML = `
      <div class="label">${escapeHtml(card.label)}</div>
      <div class="value">${escapeHtml(card.value)}</div>
      <div class="delta">${escapeHtml(card.delta)}</div>
    `;
    host.appendChild(el);
  });
  renderScopeSummary(payload);
}

function renderValueStory(payload) {
  document.getElementById("valueStoryTitle").textContent = payload.title;
  document.getElementById("valueStorySummary").textContent = payload.summary;
  document.getElementById("valueStoryMetrics").innerHTML = payload.metrics.map(valueMetricCard).join("");
  document.getElementById("valueStoryEvidence").innerHTML = payload.evidence.map(evidenceRow).join("");
  document.getElementById("valueStoryFeatures").innerHTML = payload.featureStrip
    .map((feature) => metaPill(feature))
    .join("");
}

function renderTopMerchants(payload) {
  renderHead("topMerchantsHead", payload.columns);
  renderBody("topMerchantsBody", payload.rows, payload.columns, false, "merchantId");
}

function renderReviewQueue(payload) {
  renderHead("reviewQueueHead", payload.columns);
  renderBody("reviewQueueBody", payload.rows, payload.columns);
}

function renderTransactions(payload) {
  renderHead("transactionsHead", payload.columns);
  renderBody("transactionsBody", payload.rows, payload.columns, false, "id", openTransactionDetail);
  state.transactionNextCursor = payload.nextCursor;
  const hasRows = payload.rows.length > 0;
  const totalPages = Math.max(1, Math.ceil(Number(payload.totalRows) / Math.max(1, Number(payload.pageSize))));
  const currentPage = hasRows ? state.transactionPageIndex + 1 : 1;
  const startRow = hasRows ? (state.transactionPageIndex * Number(payload.pageSize)) + 1 : 0;
  const endRow = hasRows ? startRow + payload.rows.length - 1 : 0;
  document.getElementById("previousPageButton").disabled = state.transactionPageIndex === 0;
  document.getElementById("nextPageButton").disabled = !payload.hasMore;
  document.getElementById("transactionsPagingCopy").textContent = hasRows
    ? `Showing rows ${startRow.toLocaleString("en-US")}-${endRow.toLocaleString("en-US")} of ${Number(payload.totalRows).toLocaleString("en-US")} filtered rows`
    : "No transaction rows for the current filter.";
  document.getElementById("transactionsPagingMeta").textContent =
    `Page ${currentPage.toLocaleString("en-US")} of ${totalPages.toLocaleString("en-US")} | Page size ${Number(payload.pageSize).toLocaleString("en-US")} | ${payload.hasMore ? "Next page ready" : "End of results"}`;
  document.getElementById("transactionsEmpty").classList.toggle("hidden", hasRows);
}

function renderTrends(payload) {
  renderChart("volumeChart", payload.volumeTrend);
  renderChart("declineChart", payload.declineTrend);
  renderChart("riskBreakdownChart", payload.riskBreakdown);
  renderChart("regionalStatusChart", payload.regionalStatusBreakdown);
  renderChart("paymentMethodShareChart", payload.paymentMethodShare);
  renderChart("declineReasonChart", payload.declineReasonBreakdown);
}

function renderMerchantOverview(payload) {
  const header = payload.header;
  document.getElementById("merchantDetailLabel").textContent = `${header.merchantName} | ${header.segment} | ${header.region}`;
  document.getElementById("merchantSummary").innerHTML = [
    card("Merchant", header.merchantName),
    card("Transaction Count", Number(header.transactionCount).toLocaleString("en-US")),
    card("Total Amount", new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(header.totalAmount)),
    card("Approval Rate", `${Math.round(header.approvalRate * 1000) / 10}%`)
  ].join("");
  renderChart("merchantVolumeChart", payload.volumeTrend);
  renderHead("merchantTransactionsHead", payload.columns);
  renderBody("merchantTransactionsBody", payload.rows, payload.columns);
}

function renderWorkbench(payload) {
  renderHead("riskTierStatsHead", payload.riskTierStats.columns);
  renderBody("riskTierStatsBody", payload.riskTierStats.rows, payload.riskTierStats.columns);
  renderHead("analystWorkloadHead", payload.analystWorkload.columns);
  renderBody("analystWorkloadBody", payload.analystWorkload.rows, payload.analystWorkload.columns);
  document.getElementById("workbenchComputedFields").textContent = prettyJson(payload.computedFields);
  document.getElementById("workbenchExposurePolicy").textContent = prettyJson(payload.exposurePolicy);
  document.getElementById("workbenchExecutionGuard").textContent = prettyJson(payload.executionGuard);
  document.getElementById("workbenchTelemetry").textContent = prettyJson(payload.telemetry);
  document.getElementById("workbenchJoinExplain").textContent = prettyJson(payload.joinExplain);
}

function renderQueryStudio(payload) {
  document.getElementById("naturalQueryMeta").innerHTML = [
    metaPill("Mode: Natural"),
    metaPill("Runtime vocabulary"),
    metaPill(`${payload.naturalQuery.rows.length} grouped rows`)
  ].join("");
  renderChart("naturalQueryChart", payload.naturalQuery.chart);
  renderHead("naturalQueryHead", payload.naturalQuery.columns);
  renderBody("naturalQueryBody", payload.naturalQuery.rows, payload.naturalQuery.columns);
  document.getElementById("naturalQueryText").textContent = payload.naturalQuery.queryText;
  document.getElementById("naturalQuerySchema").textContent = prettyJson(payload.naturalQuery.schema);
  document.getElementById("naturalQueryExplain").textContent = prettyJson(payload.naturalQuery.explain);

  document.getElementById("typedQueryMeta").innerHTML = [
    metaPill("Mode: Typed DSL"),
    metaPill("Code-owned filters"),
    metaPill(`${payload.typedQuery.rows.length} queue rows`)
  ].join("");
  renderHead("typedQueryHead", payload.typedQuery.columns);
  renderBody("typedQueryBody", payload.typedQuery.rows, payload.typedQuery.columns);
  document.getElementById("typedQuerySchema").textContent = prettyJson(payload.typedQuery.schema);
  document.getElementById("typedQueryExplain").textContent = prettyJson(payload.typedQuery.explain);

  document.getElementById("cancellationDemoSummary").innerHTML = [
    card("Status", payload.cancellationDemo.cancelled ? "Cancelled as expected" : "Completed before cancel"),
    card("Block Code", payload.cancellationDemo.blockCode || "NONE"),
    card("Rows Before Abort", String(payload.cancellationDemo.rowsReturnedBeforeAbort ?? 0)),
    card("Query Type", "SQL-like stream")
  ].join("");
  renderHead("cancellationDemoHead", payload.cancellationDemo.columns);
  renderBody("cancellationDemoBody", payload.cancellationDemo.rows, payload.cancellationDemo.columns);
  document.getElementById("cancellationDemoQuery").textContent = payload.cancellationDemo.queryText;
  document.getElementById("cancellationDemoGuard").textContent = prettyJson(payload.cancellationDemo.executionGuard);
}

function renderReportRun(payload) {
  document.getElementById("reportRunLabel").textContent = `${payload.name} | source: ${payload.source}`;
  renderHead("reportRowsHead", payload.columns);
  renderBody("reportRowsBody", payload.rows, payload.columns);
  document.getElementById("reportRowsEmpty").classList.toggle("hidden", payload.rows.length > 0);
  const chartWrap = document.querySelector(".report-chart-wrap");
  if (payload.chart) {
    chartWrap.classList.remove("hidden");
    renderChart("reportChart", payload.chart);
  } else {
    chartWrap.classList.add("hidden");
    destroyChart("reportChart");
  }
}

function renderReportInspect(payload) {
  document.getElementById("reportInspectLabel").textContent = `${payload.name} | query internals ready for review`;
  document.getElementById("reportQueryText").textContent = payload.queryText;
  document.getElementById("reportDefaultParams").textContent = prettyJson(payload.defaultParams);
  document.getElementById("reportSchema").textContent = prettyJson(payload.schema);
  document.getElementById("reportPlanPreview").textContent = prettyJson(payload.planPreview);
  document.getElementById("reportDiagnostics").textContent = prettyJson(payload.diagnostics);
  document.getElementById("reportPushdownPreview").textContent = prettyJson(payload.pushdownPreview);
  document.getElementById("reportExplain").textContent = prettyJson(payload.explain);
}

function renderReportMeta() {
  const current = state.reportCatalog.find((item) => item.id === state.selectedReportId);
  const host = document.getElementById("reportMeta");
  if (!current) {
    host.innerHTML = "";
    return;
  }
  host.innerHTML = [
    metaPill(`Mode: ${current.kind}`),
    metaPill(`Category: ${current.category}`),
    metaPill(current.summary),
    metaPill(`Chart: ${current.chartEnabled ? "ON" : "OFF"}`),
    metaPill(`Defaults: ${Object.keys(current.defaultParams || {}).length}`)
  ].join("");
}

function renderScopeSummary(payload) {
  const chips = [
    scopePill("Scope", payload.rangeLabel),
    scopePill("Rows", Number(payload.recordCount || 0).toLocaleString("en-US")),
    scopePill("Sort", `${pretty(state.sortBy)} ${state.sortDirection.toUpperCase()}`)
  ];
  if (state.region !== "ALL") {
    chips.push(scopePill("Region", state.region));
  }
  if (state.status !== "ALL") {
    chips.push(scopePill("Status", state.status));
  }
  if (state.riskBand !== "ALL") {
    chips.push(scopePill("Risk", state.riskBand));
  }
  if (state.segment !== "ALL") {
    chips.push(scopePill("Segment", state.segment));
  }
  if (state.search) {
    chips.push(scopePill("Search", state.search));
  }
  chips.push(scopePill("Flow", "MySQL -> POJO -> PojoLens"));
  document.getElementById("scopeSummary").innerHTML = chips.join("");
}

function renderChart(canvasId, payload) {
  const canvas = document.getElementById(canvasId);
  if (charts[canvasId]) {
    charts[canvasId].destroy();
  }
  charts[canvasId] = new Chart(canvas, payload);
}

function destroyChart(canvasId) {
  if (charts[canvasId]) {
    charts[canvasId].destroy();
    delete charts[canvasId];
  }
}

function renderHead(id, columns) {
  document.getElementById(id).innerHTML = `
    <tr>${columns.map((column) => `<th>${escapeHtml(pretty(column))}</th>`).join("")}</tr>
  `;
}

function renderBody(id, rows, columns, append = false, clickableKey = null, onClick = null) {
  const body = document.getElementById(id);
  const html = rows.map((row) => `
    <tr ${clickableKey && row[clickableKey] ? `class="clickable-row" data-click-value="${escapeHtml(String(row[clickableKey]))}"` : ""}>
      ${columns.map((column) => `<td>${formatCell(column, row[column])}</td>`).join("")}
    </tr>
  `).join("");
  if (append) {
    body.insertAdjacentHTML("beforeend", html);
  } else {
    body.innerHTML = html;
  }
  if (clickableKey) {
    body.querySelectorAll("[data-click-value]").forEach((row) => {
      row.addEventListener("click", async () => {
        if (onClick) {
          await onClick(row.dataset.clickValue);
        } else {
          state.selectedMerchantId = row.dataset.clickValue;
          await loadMerchantOverview();
        }
      });
    });
  }
}

async function openTransactionDetail(transactionId) {
  const payload = await fetchJson(`/api/transactions/${encodeURIComponent(transactionId)}`);
  document.getElementById("transactionDrawerLabel").textContent =
    `${payload.header.id} | ${payload.header.merchantName} | ${payload.header.status}`;
  document.getElementById("transactionDrawerSummary").innerHTML = [
    card("Amount", new Intl.NumberFormat("en-US", { style: "currency", currency: payload.header.currency || "USD" }).format(payload.header.amount)),
    card("Risk", `${payload.header.riskBand} (${payload.header.riskScore})`),
    card("Merchant", `${payload.header.merchantName} | ${payload.header.merchantRegion}`),
    card("Customer", `${payload.header.customerId} | ${payload.header.customerCountry}`),
    card("Review", `${payload.header.reviewStatus} | ${payload.header.reviewPriority}`),
    card("Chargeback", payload.header.chargebackStage || "NONE"),
    card("Method", `${payload.header.paymentMethod} | ${payload.header.cardBrand}`),
    card("Failure Code", payload.header.failureCode || "-")
  ].join("");
  renderHead("transactionTimelineHead", payload.eventColumns);
  renderBody("transactionTimelineBody", payload.events, payload.eventColumns);
  document.getElementById("transactionDrawer").classList.remove("hidden");
  document.getElementById("transactionDrawerBackdrop").classList.remove("hidden");
}

function closeTransactionDrawer() {
  document.getElementById("transactionDrawer").classList.add("hidden");
  document.getElementById("transactionDrawerBackdrop").classList.add("hidden");
}

function fillReportSelect() {
  const select = document.getElementById("reportSelect");
  select.innerHTML = state.reportCatalog.map((item) => `
    <option value="${escapeHtml(item.id)}"${item.id === state.selectedReportId ? " selected" : ""}>
      ${escapeHtml(item.name)}
    </option>
  `).join("");
  select.value = state.selectedReportId || "";
}

function formatCell(column, value) {
  if (value === null || value === undefined || value === "") {
    return '<span class="text-muted">-</span>';
  }
  if (column === "status") {
    return `<span class="pill status-${escapeHtml(String(value))}">${escapeHtml(String(value))}</span>`;
  }
  if (column === "riskBand") {
    const tone = value === "LOW" ? "good" : value === "MEDIUM" ? "warn" : "bad";
    return `<span class="pill tone-${tone}">${escapeHtml(String(value))}</span>`;
  }
  if (column === "amount" || column === "totalAmount") {
    return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value);
  }
  if (column === "createdAt") {
    return `<time datetime="${escapeHtml(String(value))}">${escapeHtml(new Date(value).toLocaleString())}</time>`;
  }
  return escapeHtml(String(value));
}

function fillSelect(id, values, selected, includeAll) {
  const select = document.getElementById(id);
  const options = [];
  if (includeAll) {
    options.push(`<option value="ALL">All</option>`);
  }
  values.forEach((value) => {
    const isSelected = value === selected;
    options.push(`<option value="${escapeHtml(value)}"${isSelected ? " selected" : ""}>${escapeHtml(value)}</option>`);
  });
  if (!includeAll && !values.includes(selected)) {
    options.unshift(`<option value="${escapeHtml(selected)}" selected>${escapeHtml(selected)}</option>`);
  }
  select.innerHTML = options.join("");
  select.value = selected;
}

function currentParams() {
  const params = new URLSearchParams();
  params.set("range", state.range);
  params.set("region", state.region);
  params.set("status", state.status);
  params.set("riskBand", state.riskBand);
  params.set("segment", state.segment);
  params.set("search", state.search);
  params.set("limit", String(state.limit));
  params.set("sortBy", state.sortBy);
  params.set("sortDirection", state.sortDirection);
  return params;
}

function queryString() {
  return currentParams().toString();
}

function resetTransactionPaging() {
  state.transactionPageIndex = 0;
  state.transactionPageCursors = [null];
  state.transactionNextCursor = null;
  document.getElementById("transactionsBody").innerHTML = "";
}

function hydrateNavigationStateFromUrl() {
  const rawHash = window.location.hash.startsWith("#")
    ? window.location.hash.slice(1)
    : "";
  if (!rawHash) {
    return;
  }
  const params = rawHash.includes("=")
    ? new URLSearchParams(rawHash)
    : new URLSearchParams([["tab", rawHash]]);
  const requestedTopTab = params.get("tab");
  if (TOP_LEVEL_TABS.includes(requestedTopTab)) {
    state.activeTab = requestedTopTab;
  }
  Object.values(SUBTAB_GROUPS).forEach((config) => {
    const requestedSubTab = params.get(config.urlKey);
    if (config.tabs.includes(requestedSubTab)) {
      state[config.stateKey] = requestedSubTab;
    }
  });
}

function syncNavigationUrl() {
  const params = new URLSearchParams();
  params.set("tab", state.activeTab);
  Object.values(SUBTAB_GROUPS).forEach((config) => {
    params.set(config.urlKey, state[config.stateKey]);
  });
  window.history.replaceState(
    null,
    "",
    `${window.location.pathname}${window.location.search}#${params.toString()}`
  );
}

function resizeChartsSoon() {
  window.requestAnimationFrame(() => {
    window.requestAnimationFrame(() => {
      Object.values(charts).forEach((chart) => chart.resize());
    });
  });
}

function reviewQueueQueryString() {
  const params = new URLSearchParams();
  params.set("range", state.range);
  params.set("region", state.region);
  params.set("riskBand", state.riskBand);
  params.set("segment", state.segment);
  params.set("search", state.search);
  return params.toString();
}

async function fetchJson(url) {
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
  return response.json();
}

function setStatus(text) {
  const badge = document.getElementById("statusBadge");
  badge.textContent = text;
  badge.dataset.status = text;
}

function renderError(error) {
  const panel = document.getElementById("errorPanel");
  panel.textContent = error.message;
  panel.classList.remove("hidden");
}

function clearError() {
  const panel = document.getElementById("errorPanel");
  panel.textContent = "";
  panel.classList.add("hidden");
}

function pretty(value) {
  return value.replace(/([A-Z])/g, " $1").replace(/^./, (match) => match.toUpperCase());
}

function prettyJson(value) {
  return JSON.stringify(value, null, 2);
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function debounce(fn, wait) {
  let timeoutId = null;
  return (...args) => {
    clearTimeout(timeoutId);
    timeoutId = window.setTimeout(() => fn(...args), wait);
  };
}

function card(label, value) {
  return `
    <article class="merchant-summary-card">
      <div class="label">${escapeHtml(label)}</div>
      <div class="value">${escapeHtml(value)}</div>
    </article>
  `;
}

function metaPill(text) {
  return `<span class="report-meta-pill">${escapeHtml(text)}</span>`;
}

function scopePill(label, value) {
  return `<span class="scope-pill"><strong>${escapeHtml(label)}:</strong> ${escapeHtml(value)}</span>`;
}

function valueMetricCard(metric) {
  return `
    <article class="value-metric-card">
      <div class="label">${escapeHtml(metric.label)}</div>
      <div class="value">${escapeHtml(metric.value)}</div>
      <div class="detail">${escapeHtml(metric.detail)}</div>
    </article>
  `;
}

function evidenceRow(text) {
  return `
    <article class="value-evidence-row">
      <div class="marker">PL</div>
      <div class="text">${escapeHtml(text)}</div>
    </article>
  `;
}
