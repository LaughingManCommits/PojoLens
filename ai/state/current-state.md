# Current State

## Repository Health

- Repository is a multi-module Maven Java library on Java 17 (`pojo-lens-parent` + `pojo-lens` + `pojo-lens-spring-boot-autoconfigure` + `pojo-lens-spring-boot-starter` + `pojo-lens-benchmarks`).
- Runtime consumer coordinates remain `io.github.laughingmancommits:pojo-lens:1.0.0`.
- Central release profiles now exist for deployable modules `pojo-lens`, `pojo-lens-spring-boot-autoconfigure`, and `pojo-lens-spring-boot-starter`.
- CI workflows present: `.github/workflows/ci.yml` and `.github/workflows/release.yml`.
- `TODO.md` now tracks the pre-adoption simplification roadmap (`WP7.1`-`WP7.5`), and all current work packages are now complete.
- `2026-03-21` artifact-scope split is complete: runtime jar excludes benchmark/JMH classes and benchmark tooling is isolated in `pojo-lens-benchmarks`.
- `2026-03-22` source layout split is complete: runtime code/tests/resources now live under `pojo-lens/src/...` and benchmark code/tests/resources now live under `pojo-lens-benchmarks/src/...` (no shared top-level `src` compile path).

## Latest Validation

- `2026-03-26`: fixed a real browser-side add-employee salary validation bug in
  the starter example:
  - root cause:
    the salary input used `min="1"` with `step="1000"`, which made the browser
    accept only values like `1`, `1001`, `2001`, ...
    so round values like `10000` were incorrectly rejected.
  - fix:
    changed the add-employee salary input to `step="1"` so any positive integer
    salary is accepted.
  - Playwright coverage was tightened so the happy-path add-employee flow now
    submits through the real browser submit button instead of bypassing HTML
    validation with a synthetic form event.
  - validation passed:
    `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test`
- `2026-03-26`: starter example frontend layout was reorganized around the real
  dashboard workflow:
  - top section now groups configuration controls, selector help, repo doc
    guideposts, and a compact overview strip.
  - analytical outputs now live in a single `Insight Board` section with the
    payroll chart, headcount chart, and focused stats table grouped together.
  - operational flows now live in a single `Work With Data` section with the
    employee list, direct top-paid query, and add-employee form.
  - frontend now computes and renders overview cards for active stats view,
    active chart type, employee count, department count, total payroll, and
    average salary.
  - validation passed:
    `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test`
- `2026-03-26`: starter example dashboard controls were refocused onto
  user-facing selectors instead of backend implementation modes:
  - replaced internal `statsMode/chartMode` dashboard selectors with
    `statsView/chartType`.
  - `statsView` now drives the table focus across:
    `DEPARTMENT_PAYROLL`,
    `DEPARTMENT_HEADCOUNT`,
    `TOP_3_PAYROLL_DEPARTMENTS`,
    and `TEAM_SUMMARY`.
  - `chartType` now drives presentation across:
    `BAR`,
    `PIE`,
    `LINE`,
    and `AREA`,
    while reusing the same PojoLens preset/report definitions underneath.
  - added immutable `ChartSpec.withType(...)` so chart presets/reports can
    switch presentation without rewriting the underlying query contract.
  - `DashboardPlaywrightE2eTest` now asserts the full
    `statsView x chartType` matrix, verifies actual Chart.js config type changes,
    and checks `AREA` charts keep dataset `fill=true`.
  - validations passed:
    `mvn -B -ntp -pl pojo-lens-spring-boot-starter -am install -DskipTests`
    `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test`
    `scripts/check-doc-consistency.ps1`
- `2026-03-26`: example frontend hardening completed after reproducing a real browser chart failure:
  - root cause:
    the new built-in `ChartJsPayload` dataset shape serialized nullable fields
    such as `yAxisID`, and Chart.js treated `null` as an explicit invalid scale
    selection (`TypeError: Cannot read properties of undefined (reading 'axis')`).
  - fix:
    `ChartJsDataset` now omits null optional fields during JSON serialization,
    and `ChartJsAdapterBridgeTest` now includes a serialization contract check.
  - example frontend hardening:
    the example now serves Bootstrap + Chart.js from local `/webjars/...`
    resources instead of runtime CDNs,
    exposes a visible `#clientErrorPanel`,
    and routes uncaught frontend/chart render failures into that panel.
  - Playwright hardening:
    `DashboardPlaywrightE2eTest` now asserts that the client-error panel stays
    empty and that chart instances are actually created, not just that canvases
    exist.
  - validations passed:
    `mvn -B -ntp -pl pojo-lens -am "-Dtest=ChartJsAdapterBridgeTest,ReportDefinitionTest,ChartQueryPresetsTest,StatsViewPresetsTest" test`
    `mvn -B -ntp -pl pojo-lens-spring-boot-starter -am install -DskipTests`
    `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test`
    `scripts/check-doc-consistency.ps1`
- `2026-03-26`: full repository regression passed after the dashboard-simplification pass:
  - command:
    `mvn -q test`
  - current validation baseline now includes:
    focused `pojo-lens` API tests,
    docs consistency,
    starter/example validation,
    and full multi-module regression.
- `2026-03-26`: dashboard-simplification pass completed across library + example:
  - added built-in Chart.js interop types under `pojo-lens`:
    `ChartJsAdapter`, `ChartJsPayload`, `ChartJsData`, `ChartJsDataset`.
  - added higher-level table/dashboard helpers:
    `StatsTablePayload`,
    `StatsTable.rowsAsMaps()/payload()`,
    `StatsViewPreset.tablePayload(...)`,
    and `TabularRows`.
  - added projection-free preset overloads returning `QueryRow` for the common
    stats/chart preset shapes in `StatsViewPresets` and `ChartQueryPresets`.
  - added frontend-ready helpers:
    `ChartQueryPreset.chartJs(...)`,
    `ReportDefinition.chartJs(...)`,
    and immutable chart-spec customization via
    `ChartQueryPreset.mapChartSpec(...)` /
    `ReportDefinition.mapChartSpec(...)`.
  - fixed `QueryRow` support in validator/materialization paths so grouped fast
    stats can flow through the new projection-free preset APIs.
  - simplified `examples/spring-boot-starter-basic` to consume the new library
    APIs directly:
    removed the example-local `ChartJsPayloadMapper`,
    switched preset/report chart flows to `chartJs(...)`,
    switched preset table flows to `tablePayload(...)`,
    and updated UI mode help/README wording to point at those APIs.
  - validations passed:
    `mvn -B -ntp -pl pojo-lens -am "-Dtest=StatsViewPresetsTest,ChartQueryPresetsTest,ChartJsAdapterBridgeTest,ReportDefinitionTest" test`
    `scripts/check-doc-consistency.ps1`
    `mvn -B -ntp -pl pojo-lens-spring-boot-starter -am install -DskipTests`
    `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test`
- `2026-03-26`: Java Playwright suite expanded to all frontend feature flows:
  - `DashboardPlaywrightE2eTest` now runs `6` passing tests covering:
    API surfaces, full dashboard selector matrix, runtime badges, top-paid form,
    add-employee UI submit path, and invalid add-employee error feedback path.
  - selector-matrix UI test now waits for dashboard refresh responses to avoid
    async race failures and now verifies actual rendered Chart.js types.
  - validation passed:
    `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test`
- `2026-03-26`: Java Playwright coverage was added for the starter example app:
  - added Java/JUnit Playwright E2E suite
    `DashboardPlaywrightE2eTest` in
    `examples/spring-boot-starter-basic/src/test/java/...`.
  - suite now exercises all example-app feature surfaces:
    runtime endpoint, employees/departments endpoints, top-paid endpoint,
    dashboard options endpoint, dashboard selector matrix
    (`statsView` x `chartType`), employee creation endpoint, and key dashboard
    UI flows (stats-view switching, chart-type switching, stats rendering,
    top-paid form, add-form presence).
  - switched the example test setup to Java-based Playwright only
    (removed Node Playwright harness in favor of Maven/JUnit execution).
  - updated example module build config with test dependencies
    (`spring-boot-starter-test`, `playwright`, `jackson-databind`)
    and Surefire `3.5.4`.
  - validation passed:
    `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -Dtest=DashboardPlaywrightE2eTest test`
- `2026-03-26`: starter example preset-first dashboard upgrade completed:
  - expanded `examples/spring-boot-starter-basic` API surface to demonstrate
    more PojoLens features in one flow:
    - `GET /api/employees/dashboard-options` for supported selector discovery.
    - `GET /api/employees/dashboard?statsView=...&chartType=...` for switching
      stats table focus and chart presentation while keeping PojoLens presets
      and report helpers underneath.
    - retained existing mutable employee and top-paid/runtime endpoints.
  - `statsView` now supports:
    `DEPARTMENT_PAYROLL`,
    `DEPARTMENT_HEADCOUNT`,
    `TOP_3_PAYROLL_DEPARTMENTS`,
    and `TEAM_SUMMARY`.
  - `chartType` now supports:
    `BAR`,
    `PIE`,
    `LINE`,
    and `AREA`.
  - dashboard frontend (`/`) now includes explicit stats-view and chart-type
    selectors and renders dynamic stats columns/totals/source text from the
    selected view.
  - validations passed:
    `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -DskipTests package`
    `scripts/check-doc-consistency.ps1`
- `2026-03-26`: Spring Boot starter basic example dashboard upgrade completed:
  - added a static frontend at `/` using Bootstrap + Chart.js for interactive
    employee management and chart rendering.
  - expanded `EmployeeQueryController` with mutable in-memory employee APIs:
    `GET /api/employees`,
    `POST /api/employees`,
    `GET /api/employees/departments`,
    `GET /api/employees/dashboard`,
    while keeping `top-paid` and `runtime` endpoints.
  - dashboard chart payloads are now produced from PojoLens chart mapping and
    serialized in a Chart.js-ready shape.
  - validations passed:
    `mvn -B -ntp -pl pojo-lens-spring-boot-starter -am install -DskipTests`
    `mvn -B -ntp -f examples/spring-boot-starter-basic/pom.xml -DskipTests package`
- `2026-03-26`: lint baseline refresh rerun completed:
  - lint profile:
    `mvn -B -ntp -Plint verify -DskipTests`
  - baseline refresh:
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
  - gate check:
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity: `11861` entries, `new=0`, `fixed=0`.
- `2026-03-25`: `WP7.3`-`WP7.5` delivered and validated:
  - removed the duplicate `PojoLens` query/chart facade entry methods and the
    public static/global cache-policy methods on `PojoLens`, `PojoLensSql`, and
    `PojoLensCore`.
  - kept direct static entry points on internal default singleton caches while
    making `PojoLensRuntime` the only public cache-tuning surface.
  - wired `PojoLensRuntime.parse(...)` and SQL-like aggregate execution onto the
    runtime-owned stats-plan cache so runtime-scoped policy now applies
    consistently across fluent and SQL-like paths.
  - updated runtime/cache/public-api tests, benchmark module call sites, and
    migration/docs pages (`MIGRATION.md`, `docs/caching.md`, `docs/sql-like.md`)
    to the helper-only facade and runtime-first cache model.
  - roadmap state:
    `WP7.1`-`WP7.5` `Done`.
  - validations passed:
    `mvn -q -pl pojo-lens -am test-compile`
    `mvn -q -pl pojo-lens-benchmarks -am test`
    `mvn -q test`
    `scripts/check-doc-consistency.ps1`
- `2026-03-25`: `WP7.2` facade fate decision delivered:
  - recorded the concrete pre-adoption facade decision in
    `docs/consolidation-review.md`:
    `PojoLens` should become a helper-only facade before wider adoption.
  - decided that the pure overlap aliases
    `newQueryBuilder(...)`, `parse(...)`, `template(...)`, and
    `toChartData(...)` should be removed, not merely deprecated, in the
    pre-adoption cleanup path.
  - kept `newRuntime(...)`, cursor helpers, `bundle(...)`,
    `compareSnapshots(...)`, and `report(...)` on the facade.
  - added migration wording to `MIGRATION.md` and aligned
    `docs/entry-points.md` to the helper-only target state.
  - updated `TODO.md`:
    `WP7.2` `Done`,
    `WP7.3` now `Ready`.
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`
- `2026-03-25`: `WP7.1` facade-method audit delivered:
  - added a method-level `PojoLens` facade audit to
    `docs/consolidation-review.md` for the no-users-yet simplification path.
  - classified the pure query/chart entry aliases
    (`newQueryBuilder`, `parse`, `template`, `toChartData`) as explicit-entry
    deprecation targets.
  - classified `newRuntime`, cursor helpers, `bundle`, `compareSnapshots`, and
    `report` as keep-on-facade helper surface.
  - classified the `PojoLens` SQL-like and stats-plan cache delegates as
    remove-before-wider-adoption candidates, with `PojoLensRuntime` as the
    preferred policy direction and `PojoLensSql` / `PojoLensCore` as only
    fallback global owners if static policy remains public.
  - updated `TODO.md` to mark `WP7.1` `Done`; `WP7.2` is now the next ready
    work package.
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`
- `2026-03-25`: roadmap cleanup validated:
  - removed the completed `Product Surface Realignment` roadmap from `TODO.md`
    so the file now contains only the active
    `Pre-Adoption Simplification` roadmap.
  - kept the current forward-looking work centered on facade narrowing and
    `PojoLensRuntime`-first cache-policy guidance.
  - refreshed `ai/state/current-state.md` and `ai/state/handoff.md` so startup
    context points at the active backlog instead of the retired roadmap.
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`

- `2026-03-25`: product-surface coherence review + TODO realignment validated:
  - current feature set is coherent at the engine level:
    one in-memory query runtime with fluent + SQL-like entry styles and shared execution underneath.
  - main overlap zones identified:
    entry points (`PojoLens`, `PojoLensCore`, `PojoLensSql`, `PojoLensChart`, `PojoLensRuntime`),
    reusable wrappers (`ReportDefinition`, `ChartQueryPreset`, `StatsViewPreset`),
    config paths (static/global `PojoLens.*` vs instance-scoped `PojoLensRuntime`),
    and docs/product narrative breadth.
  - rewrote `TODO.md` from completed roadmap history into a new
    `Product Surface Realignment` roadmap with six concrete spikes:
    feature-surface audit, entry-point realignment, wrapper consolidation,
    advanced-feature containment, docs/example navigation realignment,
    and consolidation/deprecation candidate review.
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`
- `2026-03-25`: realignment roadmap was made execution-ready:
  - expanded `TODO.md` with an execution board, explicit status model,
    per-spike `Priority`/`Status`, and concrete work packages (`WPx.y`) with
    `P0`/`P1`/`P2` priority and `Done`/`In progress`/`Ready`/`Planned`/`Blocked` status.
  - current execution state:
    spike 1 `In progress`,
    spikes 2-3 `Ready`,
    spikes 4-5 `Planned`,
    spike 6 `Blocked`.
  - next executable work packages:
    `WP1.2` (feature-family matrix) and `WP1.3` (feature classification).
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`
- `2026-03-25`: feature-family matrix and classification delivered:
  - added canonical product-surface classification doc:
    `docs/product-surface.md`.
  - linked the new doc from `README.md` documentation map.
  - completed roadmap work packages:
    `WP1.2` feature-family matrix,
    `WP1.3` feature classification.
  - updated roadmap state:
    spike 1 remains `In progress`,
    `WP1.4` is now `Ready`,
    spikes 2-3 remain `Ready`.
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`
- `2026-03-25`: spike 1 (`Feature Surface Audit and Positioning`) completed:
  - aligned terminology in `README.md`, `docs/modules.md`, and
    `docs/public-api-stability.md` to match the new
    `docs/product-surface.md` classification.
  - README now distinguishes:
    core query engine,
    workflow helpers,
    and integration/tooling surface.
  - modules doc now frames explicit entry points, `PojoLensRuntime`, and
    workflow helpers as layered surfaces of the same runtime artifact.
  - public API stability doc now explicitly separates product-surface families
    from `Stable`/`Advanced`/`Internal` tier guarantees.
  - roadmap state:
    `WP1.4` `Done`,
    spike 1 `Done`,
    spikes 2-3 remain `Ready`.
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`
- `2026-03-25`: spike 2 (`Entry Point Realignment`) completed:
  - added `docs/entry-points.md` as the canonical entry-point selection guide for
    `PojoLensCore`, `PojoLensSql`, `PojoLensRuntime`, `PojoLensChart`, and the
    `PojoLens` compatibility facade.
  - normalized README and user-facing docs/examples onto explicit entry points for
    new code, including `docs/usecases.md`, `docs/charts.md`, `docs/sql-like.md`,
    and related reference docs.
  - clarified `PojoLensRuntime` as the instance-scoped policy model and left
    `PojoLens` positioned as the compatibility/helper facade.
  - roadmap state:
    `WP2.1` `Done`,
    `WP2.2` `Done`,
    `WP2.3` `Done`,
    `WP2.4` `Done`,
    spike 2 `Done`,
    spike 3 remains `Ready`.
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`
- `2026-03-25`: spike 3 (`Reusable Wrapper Consolidation`) completed:
  - added `docs/reusable-wrappers.md` as the canonical wrapper capability matrix
    and decision guide for `ReportDefinition`, `ChartQueryPreset`, and
    `StatsViewPreset`.
  - aligned wrapper positioning in `docs/reports.md`, `docs/charts.md`,
    `docs/stats-presets.md`, and `docs/product-surface.md`.
  - clarified wrapper roles in code-level Javadocs for
    `ReportDefinition`, `ChartQueryPreset`, and `StatsViewPreset`,
    including bridge semantics from specialized presets back to the general
    report contract.
  - roadmap state:
    `WP3.1` `Done`,
    `WP3.2` `Done`,
    `WP3.3` `Done`,
    `WP3.4` `Done`,
    spike 3 `Done`,
    spike 4 is now `Ready`.
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`
- `2026-03-25`: spike 4 (`Advanced Feature Containment`) completed:
  - added `docs/advanced-features.md` as the grouped landing page for optional
    runtime policy, diagnostics, testing, integration, and build-time tooling.
  - split README messaging between core/workflow helper/runtime integration and
    advanced/tooling surface so first-read adoption stays centered on querying.
  - aligned `docs/public-api-stability.md` with the same core-vs-advanced
    narrative and added explicit advanced-surface landing-page guidance.
  - added explicit optional/advanced framing to deeper docs such as caching,
    telemetry, regression fixtures, snapshot comparison, metamodel generation,
    and benchmarking.
  - roadmap state:
    `WP4.1` `Done`,
    `WP4.2` `Done`,
    `WP4.3` `Done`,
    `WP4.4` `Done`,
    spike 4 `Done`,
    spike 5 is now `Ready`.
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`
- `2026-03-25`: spike 5 (`Docs and Example Navigation Realignment`) completed:
  - rewrote README navigation around explicit "start here" and "pick a path"
    sections before the capability inventory.
  - restructured `docs/usecases.md` into a decision-first path selection guide
    with compact matrices for query style, wrapper choice, output contract, and
    runtime model, followed by the scenario catalog.
  - aligned `docs/modules.md` so it explicitly points users to the selection
    guides instead of acting like the onboarding entry point.
  - roadmap state:
    `WP5.1` `Done`,
    `WP5.2` `Done`,
    `WP5.3` `Done`,
    `WP5.4` `Done`,
    spike 5 `Done`,
    spike 6 remains `Blocked` pending consolidation review.
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`
- `2026-03-25`: spike 6 (`Consolidation and Deprecation Candidate Review`) completed:
  - added `docs/consolidation-review.md` as the disposition register for
    overlapping surfaces after spikes 1-5 settled the preferred defaults.
  - recorded the current keep/de-emphasize/candidate-later positions for
    explicit entry points, the `PojoLens` compatibility facade, reusable
    wrappers, and advanced/tooling surfaces.
  - documented the only current later-stage deprecation candidate as the
    advanced static/global cache-policy surface, with migration notes pointing
    toward `PojoLensRuntime`.
  - roadmap state:
    `WP6.1` `Done`,
    `WP6.2` `Done`,
    `WP6.3` `Done`,
    `WP6.4` `Done`,
    spike 6 `Done`,
    the product-surface realignment roadmap is now complete.
  - docs/process validation:
    `scripts/check-doc-consistency.ps1`
- `2026-03-24`: lint baseline reset completed:
  - lint profile: `mvn -B -ntp -Plint verify -DskipTests`
  - baseline refresh: `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
  - gate check: `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity: `11858` entries, `new=0`, `fixed=0`.
- `2026-03-24`: test package re-organization (public/fluent/query/chart) + baseline refresh validated:
  - moved root-package test suites into dedicated packages:
    `laughing.man.commits.publicapi` (`AbstractPublicApiCoverageTest`,
    `PublicApi*CoverageTest`, `PublicSurfaceContractTest`, `StablePublicApiContractTest`),
    `laughing.man.commits.fluent` (`Aggregation*FluentTest`, `Fluent*Test`),
    `laughing.man.commits.query` (`NestedPathQueryTest`, `QueryRegressionFixtureTest`,
    `QueryTelemetryTest`),
    and aligned root chart suites to `laughing.man.commits.chart`
    (`ChartQueryPresetsTest`, `ChartResultMapper*`, `ChartResultMapperFixtures`).
  - added package docs:
    `chart/package-info.java`, `fluent/package-info.java`,
    `publicapi/package-info.java`, `query/package-info.java`.
  - updated moved tests with explicit root API imports (`PojoLens`,
    runtime/core/sql entry points) required after subpackage migration.
  - validations:
    `mvn -q -pl pojo-lens -am test-compile`
    `mvn -q -pl pojo-lens -am "-Dtest=PublicApi*Test,PublicSurfaceContractTest,StablePublicApiContractTest,Fluent*Test,Aggregation*Test,NestedPathQueryTest,Query*Test,Chart*Test" test`
    `mvn -q test`
    `scripts/check-doc-consistency.ps1`
    `mvn -B -ntp -Plint verify -DskipTests`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity unchanged: `11859` entries, `new=0`, `fixed=0`.
- `2026-03-24`: SQL-like test package migration + lint baseline refresh validated:
  - moved all `SqlLike*Test` suites from root test package `laughing.man.commits`
    into `laughing.man.commits.sqllike`.
  - updated SQL-like test package declarations/imports and added
    `pojo-lens/src/test/java/laughing/man/commits/sqllike/package-info.java`.
  - kept `SqlLikeJoinTest` join fixture rows self-contained in that suite to avoid
    cross-package access coupling with root-package fixture classes.
  - validations:
    `mvn -q -pl pojo-lens -am test-compile`
    `mvn -q -pl pojo-lens -am "-Dtest=SqlLike*Test" test`
    `mvn -q test`
    `scripts/check-doc-consistency.ps1`
    `mvn -B -ntp -Plint verify -DskipTests`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity: `11859` entries, `new=0`, `fixed=0`.
- `2026-03-24`: SQL-like docs test projection extraction validated:
  - moved local projection models from `SqlLikeDocsExamplesTest` into shared fixture helper
    `testutil/SqlLikeDocsProjections`
    (`DepartmentHeadcount`, `DepartmentHeadcountByAlias`, `PeriodHeadcount`,
    `DepartmentSalaryRank`, `DepartmentDenseRank`, `DepartmentRunningTotal`).
  - simplified `SqlLikeDocsExamplesTest` to consume shared projection fixtures and removed
    in-class nested projection declarations.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeDocsExamplesTest,SqlLikeWindowFunctionTest,SqlLikeErrorCodesContractTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - lint profile + baseline gate:
    `mvn -B -ntp -Plint verify -DskipTests`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
  - current baseline/report parity unchanged: `11839` entries, `new=0`, `fixed=0`.
- `2026-03-24`: selective-materialization test organization + lint baseline refresh validated:
  - extracted `FilterQueryBuilderSelectiveMaterializationTest` local fixture scaffolding into
    shared helper `testutil/SelectiveMaterializationFixtures`
    (sample builders, row-field-name helper, and source/projection test models).
  - slimmed `builder/FilterQueryBuilderSelectiveMaterializationTest` to behavior-focused test flows
    by consuming shared fixtures instead of in-class data/model declarations.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=FilterQueryBuilderSelectiveMaterializationTest,FilterCoreTest,FastPojoFilterSupportTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - lint profile + baseline refresh:
    `mvn -B -ntp -Plint verify -DskipTests`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity: `11839` entries, `new=0`, `fixed=0`.
- `2026-03-24`: chart test-organization extraction + lint baseline refresh validated:
  - expanded shared chart fixtures in `testutil/ChartTestFixtures` with:
    deterministic interop dataset builders (`interopEmployeeEvents`, `interopMonthlyDepartmentPayroll`,
    `interopMonthlyDepartmentPayrollWithGaps`, `interopScatterSignals`),
    plus reusable row models/constructors (`DepartmentPeriodPayrollRow`, `ScatterSignalRow`).
  - simplified chart interop suites by removing inline synthetic data/model clutter:
    `chart/ChartLibraryInteropTest` now consumes shared builders + models;
    `chart/ChartJsAdapterBridgeTest` now reuses shared period-payroll row fixtures.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=ChartLibraryInteropTest,ChartJsAdapterBridgeTest,FluentChartIntegrationTest,SqlLikeChartIntegrationTest,ChartQueryPresetsTest,StatsDocsExamplesTest,StatsViewPresetsTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - lint profile + baseline refresh:
    `mvn -B -ntp -Plint verify -DskipTests`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity: `11810` entries, `new=0`, `fixed=0`.
- `2026-03-24`: expanded fixture dedup pass + lint baseline refresh validated:
  - added shared fixture models:
    `testutil/SqlLikeProjectionFixtures` (`ComputedBoostProjection`, `ComputedScalarProjection`),
    `ChartTestFixtures.EmployeeEvent`,
    and `WindowTestFixtures.DepartmentAgg`.
  - migrated duplicated nested fixture models out of tests:
    `SqlLikeJoinTest` (`ParentBean`, `ChildBean` -> `PojoLensBehaviorFixtures`),
    `ChartJsAdapterBridgeTest` + `ChartLibraryInteropTest` (`EmployeeEvent`),
    `FluentWindowFunctionTest` + `SqlLikeMappingParityTest` (`DepartmentAgg`),
    `SqlLikeQueryContractTest` + `SqlLikeValidationTest` (`ComputedProjection` variants),
    and stats preset/docs tests to shared `ChartTestFixtures.DepartmentPayrollRow`.
  - removed now-redundant `StatsExampleFixtures.DepartmentPayrollRow`.
  - duplicate nested test fixture class-name scan now reports no duplicates.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=ChartQueryPresetsTest,FluentWindowFunctionTest,SqlLikeErrorCodesContractTest,SqlLikeJoinTest,SqlLikeMappingParityTest,SqlLikeQueryContractTest,SqlLikeValidationTest,StatsDocsExamplesTest,StatsViewPresetsTest,ChartJsAdapterBridgeTest,ChartLibraryInteropTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - lint profile + baseline refresh:
    `mvn -B -ntp -Plint verify -DskipTests`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity: `11841` entries, `new=0`, `fixed=0`.
- `2026-03-24`: common stats projection dedup + lint baseline refresh validated:
  - added shared projection fixture helper `testutil/CommonStatsProjections` with reusable:
    `DepartmentCount`, `DepartmentCountAlias`, and `DepartmentCountRow`.
  - migrated repeated nested projection models in:
    `CacheConcurrencyTest`, `CachePolicyConfigTest`,
    `SqlLikeParametersContractTest`, `SqlLikeStrictParameterTypeModeTest`,
    `SqlLikeTypedBindContractTest`, `SqlLikeQueryContractTest`,
    `ExplainToolingTest`,
    `ReportDefinitionTest`, `QueryRegressionFixtureTest`,
    `QueryTelemetryTest`, and `DatasetBundleExecutionContextTest`.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=CacheConcurrencyTest,CachePolicyConfigTest,SqlLikeParametersContractTest,SqlLikeStrictParameterTypeModeTest,SqlLikeTypedBindContractTest,ExplainToolingTest,QueryRegressionFixtureTest,QueryTelemetryTest,ReportDefinitionTest,DatasetBundleExecutionContextTest,FluentWindowFunctionTest,SqlLikeWindowFunctionTest,SqlLikeMappingParityTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - lint profile + baseline refresh:
    `mvn -B -ntp -Plint verify -DskipTests`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity: `11908` entries, `new=0`, `fixed=0`.
- `2026-03-24`: window/parity test fixture dedup pass validated:
  - added shared window fixture helper `testutil/WindowTestFixtures` for repeated window input/projection models and sample aggregate-window rows.
  - migrated `FluentWindowFunctionTest`, `SqlLikeWindowFunctionTest`, and `SqlLikeMappingParityTest` to shared fixtures, removing duplicated nested model classes.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=FluentWindowFunctionTest,SqlLikeWindowFunctionTest,SqlLikeMappingParityTest,PublicApiCacheCoverageTest,PublicApiSqlCoverageTest,PublicApiFluentCoverageTest,PublicApiEcosystemCoverageTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - lint profile + baseline gate:
    `mvn -B -ntp -Plint verify -DskipTests`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity unchanged: `12023` entries, `new=0`, `fixed=0`.
- `2026-03-24`: public API coverage split + lint baseline refresh validated:
  - split monolithic `PublicApiCoverageTest` into focused suites:
    `PublicApiCacheCoverageTest`, `PublicApiSqlCoverageTest`,
    `PublicApiFluentCoverageTest`, `PublicApiEcosystemCoverageTest`.
  - added shared coverage base + models:
    `AbstractPublicApiCoverageTest`, `testutil/PublicApiModels`.
  - removed legacy `PublicApiCoverageTest` and restored fluent streaming API coverage in fluent suite.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=PublicApiCacheCoverageTest,PublicApiSqlCoverageTest,PublicApiFluentCoverageTest,PublicApiEcosystemCoverageTest" test`
  - expanded fixture regression:
    `mvn -q -pl pojo-lens -am "-Dtest=StatsDocsExamplesTest,ChartQueryPresetsTest,StatsViewPresetsTest,FluentChartIntegrationTest,SqlLikeChartIntegrationTest,TimeBucketAggregationTest,TimeBucketUtilTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - lint profile + baseline refresh:
    `mvn -B -ntp -Plint verify -DskipTests`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity: `12023` entries, `new=0`, `fixed=0`.
- `2026-03-24`: expanded test-dedup pass validated:
  - added shared test-date helper `testutil/TestDateFixtures` with UTC overloads.
  - added shared chart fixture models/builders `testutil/ChartTestFixtures`.
  - added shared time-bucket fixture models/builders `testutil/TimeBucketTestFixtures`.
  - migrated additional suites to shared fixtures:
    `FluentChartIntegrationTest`, `SqlLikeChartIntegrationTest`,
    `TimeBucketAggregationTest`, `util/TimeBucketUtilTest`
    (plus previously migrated stats/chart preset tests).
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=StatsDocsExamplesTest,ChartQueryPresetsTest,StatsViewPresetsTest,FluentChartIntegrationTest,SqlLikeChartIntegrationTest,TimeBucketAggregationTest,TimeBucketUtilTest,PublicApiCoverageTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-24`: test-structure cleanup pass validated:
  - added shared test fixture helper `testutil/StatsExampleFixtures` for repeated stats/docs/chart test rows and UTC-date helper.
  - migrated `StatsDocsExamplesTest`, `ChartQueryPresetsTest`, and `StatsViewPresetsTest` to shared fixtures, removing duplicate local fixture classes/builders.
  - documented test-layer strategy and fixture-reuse guidance in `CONTRIBUTING.md`.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=StatsDocsExamplesTest,ChartQueryPresetsTest,StatsViewPresetsTest,PublicApiCoverageTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-24`: lint baseline refresh passed after stats-preset spike:
  - lint profile: `mvn -B -ntp -Plint verify -DskipTests`
  - baseline refresh: `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
  - gate check: `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity: `12090` entries, `new=0`, `fixed=0`.
- `2026-03-24`: predefined stats views spike 5 (easy usage presets) passed:
  - added new preset API: `StatsViewPresets.summary(...)`, `StatsViewPresets.by(...)`, and `StatsViewPresets.topNBy(...)`.
  - added immutable table payload contract `StatsTable<T>` with `rows`, optional `totals`, and `schema`.
  - added executable preset wrapper `StatsViewPreset<T>` with list/map/join-bundle overloads and `ReportDefinition` bridge.
  - added docs + examples in `README.md`, `docs/stats-presets.md`, `docs/usecases.md`, and `docs/reports.md`.
  - added regression/public-surface coverage:
    `StatsViewPresetsTest`, `StatsDocsExamplesTest`, `PublicApiCoverageTest`.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=StatsViewPresetsTest,StatsDocsExamplesTest,PublicApiCoverageTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-23`: SQL window spike 4 (API/docs hardening) passed:
  - docs/README now document aggregate-window syntax limits and practical recipes for top-N per group, dense rank, and running total usage.
  - SQL-like docs and public API examples now include aggregate-window parse/filter/explain coverage.
  - benchmark module now includes window-overhead JMH scenarios and a dedicated args suite (`scripts/benchmark-suite-window.args`), with notes added to `docs/benchmarking.md`.
  - TODO spike-4 checklist is marked complete.
  - focused regression: `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeDocsExamplesTest,PublicApiCoverageTest" test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-23`: lint baseline refresh passed after spike-4 closure:
  - lint profile: `mvn -B -ntp -Plint verify -DskipTests`
  - baseline refresh: `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
  - gate check: `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
  - current baseline/report parity: `11896` entries, `new=0`, `fixed=0`.
- `2026-03-23`: SQL window spike 3 (aggregate windows) passed:
  - parser/AST now supports `SUM/AVG/MIN/MAX/COUNT(...) OVER (...)` with aggregate-window argument metadata (`COUNT(*)` vs value field).
  - parser guardrails now enforce initial frame mode `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW` for aggregate windows and reject unsupported frames.
  - fluent window execution now computes running aggregate frames (`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`) with null/type parity matching existing aggregate behavior.
  - SQL-like binder/validator/join-canonicalization now compile aggregate window definitions into fluent `addWindow(...)` value-argument form.
  - SQL-like aliased projection path now casts alias-select values against projection field types before assignment, fixing numeric assignment mismatches in aggregate window projections.
  - added/updated coverage:
    `FluentWindowFunctionTest`, `SqlLikeWindowFunctionTest`, `SqlLikeParserTest`, `SqlLikeMappingParityTest`, `PublicApiCoverageTest`, `StablePublicApiContractTest`.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=FluentWindowFunctionTest,SqlLikeWindowFunctionTest,SqlLikeParserTest,SqlLikeMappingParityTest,ExplainToolingTest,SqlLikeDocsExamplesTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest,StablePublicApiContractTest,PublicSurfaceContractTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-23`: SQL-like window/qualify execution now compiles through fluent path and passed:
  - SQL-like binder now maps window select outputs to fluent `addWindow(...)` and maps `QUALIFY` predicates/boolean groups to fluent `addQualify(...)`/`addQualifyAllOf(...)`.
  - SQL-like raw-row execution now delegates to fluent filter execution (`FilterImpl`) instead of maintaining a separate SQL-like window/qualify runtime branch.
  - SQL-like explain stage-row counting now evaluates `QUALIFY` through fluent runtime as well (window+qualify applied via fluent builder execution on staged rows).
  - `ReflectionUtil.toClassList(...)` now supports direct `QueryRow` projection passthrough, allowing SQL-like internals to consume fluent raw rows safely.
  - removed unused SQL-like-specific runtime helpers (`SqlLikeWindowSupport`, `SqlLikeQualifySupport`) after fluent unification.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=FluentWindowFunctionTest,SqlLikeWindowFunctionTest,SqlLikeParserTest,SqlLikeMappingParityTest,ExplainToolingTest,SqlLikeDocsExamplesTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest,StablePublicApiContractTest,PublicSurfaceContractTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-23`: Fluent window/qualify parity passed:
  - fluent `QueryBuilder` now supports rank window outputs (`ROW_NUMBER`, `RANK`, `DENSE_RANK`) and `QUALIFY` predicates via new fluent APIs.
  - fluent execution now applies window computation then qualify filtering for non-aggregate query shapes, aligning with SQL-like stage semantics.
  - fast-path guards now bypass incompatible fast execution paths when fluent windows/qualify are configured.
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=FluentWindowFunctionTest,SqlLikeWindowFunctionTest,SqlLikeParserTest,SqlLikeMappingParityTest,SqlLikeDocsExamplesTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest,StablePublicApiContractTest,PublicSurfaceContractTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-23`: SQL window spike 2 (`QUALIFY`) passed:
  - parser/AST grammar now supports `QUALIFY` with boolean predicates and clause-order enforcement (`WHERE -> window compute -> QUALIFY -> ORDER/LIMIT/OFFSET`)
  - validation now enforces non-aggregate query shape, requires at least one window select output, and rejects unknown/subquery references in `QUALIFY`
  - execution now applies `QUALIFY` after window computation and before query-level ordering/pagination, with explain stage row counts including `qualify`
  - direct window-expression predicates in `QUALIFY` are normalized to matching window aliases
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeWindowFunctionTest,SqlLikeParserTest,ExplainToolingTest,SqlLikeDocsExamplesTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-23`: SQL window-functions MVP (`OVER`) passed:
  - parser/AST support added for `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()` with `OVER (PARTITION BY ... ORDER BY ...)`
  - execution support added for window-value computation after `WHERE` and before query-level `ORDER BY`/pagination/projection
  - deterministic tie handling for non-unique window `ORDER BY` uses stable source-row order fallback
  - validation requires `OVER(... ORDER BY ...)` and restricts window selects to non-aggregate query shapes
  - focused regression:
    `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeWindowFunctionTest,SqlLikeParserTest,SqlLikeDocsExamplesTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-22`: SQL-like maintainability refactor slices 2/3 + lint baseline refresh passed:
  - extracted SQL-like execution flow/stage telemetry internals out of `SqlLikeQuery` into new package-private helper `SqlLikeExecutionFlowSupport`
  - extracted SQL-like tokenization internals (`tokenize`, token model, position map) out of `SqlLikeParser` into new package-private helper `SqlLikeTokenizationSupport`
  - size reductions: `SqlLikeQuery` `1002 -> 748` lines, `SqlLikeParser` `1292 -> 1070` lines
  - focused SQL-like regression: `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeDocsExamplesTest,SqlLikeParserTest,SqlLikeMappingParityTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - lint profile + baseline refresh:
    `mvn -B -ntp -Plint verify -DskipTests`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot . -WriteBaseline`
    `scripts/check-lint-baseline.ps1 -Report target/checkstyle-result.xml -Baseline scripts/checkstyle-baseline.txt -RepoRoot .`
- `2026-03-22`: SQL-like maintainability refactor slice passed:
  - extracted prepared-execution cache/binding internals out of `SqlLikeQuery` into new package-private helper `SqlLikePreparedExecutionSupport`
  - focused SQL-like regression: `mvn -q -pl pojo-lens -am "-Dtest=SqlLikeDocsExamplesTest,SqlLikeParserTest,SqlLikeMappingParityTest,SqlLikeErrorCodesContractTest,PublicApiCoverageTest" test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-22`: starter distribution + integration hardening passed:
  - starter module integration smoke test (app context + endpoint): `mvn -q -pl pojo-lens-spring-boot-starter -am test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - release-path package validation for publishable modules:
    `mvn -B -ntp -pl pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter -am -Prelease-central -DskipTests package`
- `2026-03-22`: Spring Boot dependency baseline updated to `4.0.4` in parent BOM import and validated with:
  - focused module regression: `mvn -q -pl pojo-lens-spring-boot-autoconfigure -am test`
  - full regression: `mvn -q test`
- `2026-03-22`: Added runnable Spring Boot starter example project (`examples/spring-boot-starter-basic`) and validated with:
  - local starter install for example resolution: `mvn -q -pl pojo-lens-spring-boot-starter -am install -DskipTests`
  - example package: `mvn -q -f examples/spring-boot-starter-basic/pom.xml -DskipTests package`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-22`: Spring Boot starter/autoconfigure baseline passed:
  - focused module regression: `mvn -q -pl pojo-lens-spring-boot-autoconfigure -am test`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-22`: `mvn -q test` passed after moving all source/test/resource trees into module-local `src` directories and simplifying module POM source configuration.
- `2026-03-22`: `scripts/check-doc-consistency.ps1` passed after source-layout migration.
- `2026-03-20`: `mvn -q test` passed after pagination changes (`OFFSET` support + docs/tests updates).
- `2026-03-20`: focused suite passed (`SqlLikeDocsExamplesTest`, `SqlLikeParserTest`, `SqlLikeMappingParityTest`, `ExplainToolingTest`, `PublicApiCoverageTest`).
- `2026-03-20`: `scripts/check-doc-consistency.ps1` passed.
- `2026-03-21`: focused SQL-like suite passed (`SqlLikeParserTest`, `SqlLikePaginationParameterSupportTest`, `SqlLikeDocsExamplesTest`, `SqlLikeErrorCodesContractTest`, `SqlLikeStrictParameterTypeModeTest`).
- `2026-03-21`: `mvn -q test` passed after adding SQL-like `LIMIT/OFFSET` named parameter support.
- `2026-03-21`: `scripts/check-doc-consistency.ps1` passed.
- `2026-03-21`: expanded focused suite passed after keyset cursor primitives (`SqlLikeKeysetCursorTest`, `SqlLikeDocsExamplesTest`, `SqlLikeErrorCodesContractTest`, `PublicApiCoverageTest`, plus parser/lint/explain/strict suites).
- `2026-03-21`: `mvn -q test` passed after adding first-class SQL-like keyset cursor API and token support.
- `2026-03-21`: `scripts/check-doc-consistency.ps1` passed after keyset docs updates.
- `2026-03-21`: focused streaming suite passed (`StreamingExecutionOutputTest`, `SqlLikeDocsExamplesTest`, `PublicApiCoverageTest`).
- `2026-03-21`: `mvn -q test` passed after adding fluent + SQL-like streaming APIs (`iterator`/`stream`) with simple-query lazy fast path and complex-query fallback.
- `2026-03-21`: `scripts/check-doc-consistency.ps1` passed after streaming docs updates.
- `2026-03-21`: streaming benchmark suite (`StreamingExecutionJmhBenchmark`) executed with forked warmed run (`-f 1 -wi 1 -i 3 -prof gc`) and results captured in `target/benchmarks/streaming-execution-forked.json`.
- `2026-03-21`: `mvn -q test` passed after adding streaming benchmark scaffolding and benchmark docs/TODO updates.
- `2026-03-21`: `scripts/check-doc-consistency.ps1` passed after adding streaming tradeoff notes to `docs/benchmarking.md`.
- `2026-03-21`: focused index suite passed (`OptionalIndexExecutionTest`, `IndexHintJmhBenchmarkParityTest`, `PublicApiCoverageTest`) after adding fluent optional index hints and indexed candidate narrowing.
- `2026-03-21`: full `mvn -q test` passed after index API/prototype/docs updates.
- `2026-03-21`: doc consistency passed after index use-case + benchmarking updates.
- `2026-03-21`: index benchmark suites executed:
  - warm forked run: `target/benchmarks/index-hint-forked.json` (`-f 1 -wi 1 -i 3 -prof gc`)
  - cold forked run: `target/benchmarks/index-hint-cold.json` (`-f 1 -wi 0 -i 1 -prof gc`)
- `2026-03-21`: updated `TODO.md` platform hardening backlog with explicit artifact/module boundary slimming spike and validated docs consistency (`scripts/check-doc-consistency.ps1`).
- `2026-03-21`: stable API hardening validations passed:
  - focused API suites: `StablePublicApiContractTest`, `PublicSurfaceContractTest`, `PublicApiCoverageTest`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-21`: binary compatibility hardening validations passed:
  - baseline tag worktree install: `v1.0.0` -> `mvn -q -DskipTests install`
  - compatibility check: `mvn -q -Pbinary-compat -DskipTests -Dcompat.baseline.version=1.0.0 verify`
  - full regression: `mvn -q test`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
- `2026-03-21`: artifact/module-boundary slimming validations passed:
  - release path package: `mvn -B -ntp -Prelease-central -DskipTests package` (runtime javadocs now pass with benchmark source exclusion)
  - full regression: `mvn -ntp test`
  - binary compatibility: `mvn -q "-Pbinary-compat" "-DskipTests" "-Dcompat.baseline.version=1.0.0" verify`
  - docs guardrail: `scripts/check-doc-consistency.ps1`
  - runtime jar scope assertion: `jar tf target/pojo-lens-1.0.0.jar | Select-String 'laughing/man/commits/benchmark/'` -> `0`
- `2026-03-24`: lint baseline was refreshed after stats-preset spike closure (`scripts/checkstyle-baseline.txt`, `12090` entries) and currently reports `new=0`, `fixed=0`.

## Release Status

- Central namespace is verified for `io.github.laughingmancommits`.
- Release workflow supports tag-triggered (`v*`) and manual publish.
- Release workflow now validates root version and deploys module set
  `pojo-lens,pojo-lens-spring-boot-autoconfigure,pojo-lens-spring-boot-starter` with `-Prelease-central` (benchmark module remains deploy-skipped).
- Most recent Central publish reached bundle upload but failed signature verification because Central could not find the signer public key.
- Public key was then uploaded to `keyserver.ubuntu.com` and `keys.openpgp.org`; publish should be retried after keyserver propagation.

## Active Work

- `TODO.md` now contains one active roadmap only:
  `Pre-Adoption Simplification`.
  - the completed `Product Surface Realignment` roadmap is now retired from the
    active backlog and retained only through the docs baseline it produced.
  - roadmap execution is complete:
    `WP7.1`-`WP7.5` are all `Done`.
  - the facade/cache simplification goal is delivered:
    `PojoLens` is helper-only and `PojoLensRuntime` is the only public
    cache-policy tuning surface.
  - next work is optional and not currently queued in `TODO.md`
    (release retry, release-note cleanup, or further tightening review).
  - the Spring Boot starter basic example is now structured as a reference-style
    example rather than a single-file demo, with repo doc pointers embedded in
    both backend/frontend code and the example README.
  - the library now has first-class dashboard simplifiers for common frontend
    flows:
    built-in Chart.js payload mapping,
    projection-free stats/chart presets,
    and table/chart payload helpers that reduce example-level adapter code.
  - `docs/consolidation-review.md` now contains the full `PojoLens` method
    audit plus the concrete helper-only facade decision for the four pure
    overlap entry aliases.
  - the current source-of-truth docs for the already-finished surface analysis
    remain:
    `docs/product-surface.md`,
    `docs/entry-points.md`,
    `docs/reusable-wrappers.md`,
    `docs/advanced-features.md`,
    and `docs/consolidation-review.md`.
- SQL window analytics spike 1 (Window Functions MVP) completed:
  - Added SQL-like parser/AST support for rank window syntax:
    `ROW_NUMBER()/RANK()/DENSE_RANK() OVER (PARTITION BY ... ORDER BY ...)`.
  - Added SQL-like execution-stage window computation that runs after `WHERE` and before final projection.
  - Query-level `ORDER BY` now supports window aliases for window-enabled query shapes.
  - Determinism guardrails:
    missing window `ORDER BY` now fails validation; non-unique window ordering uses stable source-row tie fallback.
  - Added parser/runtime/docs coverage:
    `SqlLikeWindowFunctionTest` + parser/docs updates.
  - Updated SQL-like docs and marked TODO window spike item complete.
- SQL window analytics spike 2 (`QUALIFY`) completed:
  - Added parser/AST support for `QUALIFY` predicates with boolean expression support and clause-order guardrails.
  - Added validator support for window-only `QUALIFY` references (aliases + direct matching window expressions), including subquery rejection and non-aggregate-only enforcement.
  - Added execution-stage filtering for `QUALIFY` after window computation.
  - Explain payloads now include `qualifyRuleCount` and `stageRowCounts.qualify`.
  - Added parser/runtime/explain/docs test coverage (`SqlLikeParserTest`, `SqlLikeWindowFunctionTest`, `ExplainToolingTest`, `SqlLikeDocsExamplesTest`).
  - Updated SQL-like docs and marked TODO `QUALIFY` spike item complete.
- SQL window analytics spike 3 (Aggregate Windows Phase 2) completed:
  - Added parser/AST support for aggregate windows:
    `SUM/AVG/MIN/MAX/COUNT(...) OVER (...)` with `COUNT(*)` and value-argument metadata.
  - Added aggregate-window frame guardrails for initial supported mode:
    `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`.
  - Added fluent runtime support for running aggregate frame computation (`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`) and kept SQL-like execution compiled through fluent builder/runtime path.
  - Added coverage for parser/runtime/parity/API contract updates (`FluentWindowFunctionTest`, `SqlLikeWindowFunctionTest`, `SqlLikeParserTest`, `SqlLikeMappingParityTest`, `PublicApiCoverageTest`, `StablePublicApiContractTest`).
  - Updated TODO and marked aggregate-window spike item complete.
- SQL window analytics spike 4 (API/docs hardening) completed:
  - Added SQL-like docs hardening for aggregate-window contract/limitations and practical recipes (`top N per group`, `dense rank`, `running total`).
  - Added public API/docs regression coverage for aggregate-window parse/filter/explain paths.
  - Added benchmark comparisons for windowed vs non-windowed SQL-like queries and documented overhead notes.
  - Updated TODO and marked spike-4 items complete.
- Predefined stats views spike 5 (easy usage presets) completed:
  - Added table-first preset API `StatsViewPresets` with standard view shapes:
    `summary()`, `by(field)`, and `topNBy(field, metric, n)`.
  - Preset execution compiles to SQL-like query contracts (`SqlLikeQuery`) and reuses existing runtime execution (no separate engine).
  - Added immutable table payload `StatsTable<T>` carrying `rows`, optional `totals`, and `schema` metadata.
  - Added executable preset wrapper `StatsViewPreset<T>` with list/map/join-bindings/dataset-bundle overloads.
  - Added docs + practical recipes for grouped stats tables and leaderboard tables.
  - Added regression and public API coverage tests for preset correctness and stable output columns.
  - Updated TODO and marked spike-5 checklist complete.
- Test structure hardening (incremental) started:
  - introduced shared fixture layer for stats/docs/chart preset suites in `pojo-lens/src/test/java/laughing/man/commits/testutil/StatsExampleFixtures.java`.
  - reduced duplicated date/sample-row and projection fixture classes across multiple tests.
  - expanded fixture layer with `TestDateFixtures`, `ChartTestFixtures`, and `TimeBucketTestFixtures` and migrated additional chart/time-bucket suites.
  - extracted SQL-like docs projection models into `testutil/SqlLikeDocsProjections`.
  - extracted selective-materialization test data/models into dedicated `testutil/SelectiveMaterializationFixtures` helper.
  - moved heavy chart interop synthetic data/model code out of chart tests and into shared `ChartTestFixtures` builders/rows.
  - split `PublicApiCoverageTest` into focused cache/sql/fluent/ecosystem suites with shared base/model fixtures, and removed legacy monolithic class.
  - added shared `WindowTestFixtures` and migrated window/parity suites (`FluentWindowFunctionTest`, `SqlLikeWindowFunctionTest`, `SqlLikeMappingParityTest`) off duplicated nested window models.
  - added shared `CommonStatsProjections` and migrated repeated stats projection rows across cache/sql-like/report/telemetry/bundle suites.
  - added shared SQL-like projection fixtures (`SqlLikeProjectionFixtures`) and shared chart/window fixture rows (`EmployeeEvent`, `DepartmentAgg`) for additional cross-suite dedup.
  - aligned stats preset/docs suites on `ChartTestFixtures.DepartmentPayrollRow` and removed duplicate `DepartmentPayrollRow` fixture definition.
  - duplicate nested test fixture class-name scan now reports zero duplicates.
  - added explicit test organization/fixture guidance to `CONTRIBUTING.md`.
- Fluent parity for window analytics delivered:
  - Added fluent API contracts for rank windows and qualify rules (`addWindow(...)`, `addQualify(...)`, qualify group helpers).
  - Added fluent runtime window and qualify stages with guardrails matching SQL-like semantics (non-aggregate-only + qualify-window reference validation).
  - Added fluent<->SQL-like parity coverage for `ROW_NUMBER ... QUALIFY` and fluent-specific behavior tests.
  - SQL-like binder now compiles window/qualify configuration into fluent query-builder state, and SQL-like raw execution now runs through fluent `FilterImpl` flow.
  - SQL-like explain stage-row-count simulation now also routes qualify evaluation via fluent; SQL-like-specific window/qualify helper classes were removed.
- Pagination spike 1 completed:
  - Fluent + SQL-like `OFFSET` implemented.
  - SQL-like pagination now supports named parameters in `LIMIT/OFFSET` with integer/non-negative validation.
  - First-class SQL-like keyset cursor API is implemented (`SqlLikeCursor`, `keysetAfter`, `keysetBefore`) with cursor token support.
  - Cursor contract is documented (token format, required sort-field matching, non-null value requirements).
  - Tie-heavy/large dataset behavior is validated in tests.
  - Explain payloads include `offset`.
  - SQL-like grammar supports `OFFSET` and `LIMIT ... OFFSET`.
  - Docs/tests include offset, pattern-based keyset pagination, and first-class cursor flows.
- Streaming spike 2 completed:
  - New fluent `Filter` streaming APIs: `iterator(...)` and `stream(...)`.
  - New SQL-like streaming APIs: `SqlLikeQuery.stream/iterator(...)` and `SqlLikeBoundQuery.stream/iterator()`.
  - Simple POJO-source non-joined/non-aggregate/non-ordered queries now stream lazily row-by-row (no full result materialization).
  - Complex shapes (join/group/having/ordered windows/stats paths) fall back to list-backed streams for deterministic behavior.
  - Docs/tests cover fluent + SQL-like stream usage.
  - Benchmarks now document short-circuit tradeoffs (`stream().limit(50)` vs list materialization) with large allocation/latency wins for first-page consumers.
- Optional index spike 3 completed:
  - Fluent API now supports optional index hints via `addIndex(String)` and `addIndex(FieldSelector<...>)`.
  - Query explain now surfaces configured `indexes`.
  - Execution prototype narrows simple POJO filter candidates through indexed equality predicates and falls back safely to scan when inapplicable.
  - Added parity and behavior coverage tests (`OptionalIndexExecutionTest`, `IndexHintJmhBenchmarkParityTest`, `PublicApiCoverageTest` updates).
  - Benchmark notes now include warm/cold gain-loss tradeoffs for index hints.
- Stable API surface spike 4 started and baseline delivered:
  - Added explicit stability policy doc: `docs/public-api-stability.md` (stable/advanced/internal tiers, inclusion rules, compatibility/deprecation policy).
  - Linked stability policy from `README.md` and `docs/modules.md`.
  - Added stable contract enforcement test: `StablePublicApiContractTest` (entry-point method presence + baseline fluent/SQL-like/runtime behavior flows).
  - Marked TODO platform hardening stable-API item complete.
- Binary compatibility spike 5 baseline delivered:
  - Added `binary-compat` Maven profile with `japicmp` (`pom.xml`) gated by `compat.baseline.version`.
  - Scoped compatibility checks to the documented stable API types and enabled fail-on binary/source incompatibilities.
  - Added CI `binary-compat` job (`.github/workflows/ci.yml`) that resolves latest `v*` tag baseline, installs baseline artifact via detached worktree, and runs compatibility verification.
  - Added local run guidance to `CONTRIBUTING.md`.
  - Updated stable API policy enforcement note in `docs/public-api-stability.md`.
  - Marked TODO binary compatibility item complete.
- Artifact/module boundary spike 6 delivered:
  - Converted root build to parent POM (`pojo-lens-parent`) with modules `pojo-lens` and `pojo-lens-benchmarks`.
  - Runtime module now owns module-local runtime source/tests/resources under `pojo-lens/src/...` and participates in `release-central` publishing alongside Spring Boot integration modules.
  - Benchmark module now owns module-local benchmark source/tests/resources under `pojo-lens-benchmarks/src/...`, plus JMH annotation processing and benchmark runner shading.
  - Runtime test coupling to benchmark classes was removed (`FilterImplFastPathTest` now uses local fixture generation).
  - Benchmark tests were updated to module-aware resource paths.
  - CI now includes runtime artifact-scope assertion to block benchmark-class bleed into runtime jar.
  - TODO artifact-slimming item is marked complete.
- Spring Boot integration baseline delivered:
  - Added `pojo-lens-spring-boot-autoconfigure` with `pojo-lens.*` configuration properties and `PojoLensRuntime` auto-configuration.
  - Added optional Micrometer telemetry bridge (`QueryTelemetryListener`) with low-cardinality `stage`/`query_type` tags.
  - Added `pojo-lens-spring-boot-starter` for simplified Boot dependency wiring.
  - Starter now includes `micrometer-core` to prevent Boot 4 classpath introspection failure when evaluating micrometer-conditional auto-config methods.
  - Parent build now imports Spring Boot BOM version `4.0.4` for starter/autoconfigure dependency alignment.
  - Added standalone runnable example app at `examples/spring-boot-starter-basic` with starter-driven `PojoLensRuntime` injection and a basic SQL-like REST use case.
  - Added starter integration smoke test `PojoLensStarterSmokeIntegrationTest` (context + endpoint).
  - Updated release policy to publish starter/autoconfigure artifacts to Central alongside runtime artifact.
  - Added auto-configuration tests covering defaults, property overrides, bean backoff, and micrometer enable/disable behavior.
  - Updated README/modules documentation with starter usage and property examples.
- SQL-like maintainability hardening delivered (slices 1-3):
  - `SqlLikeQuery` prepared-execution/binding cache internals were split into new helper class `SqlLikePreparedExecutionSupport`.
  - `SqlLikeQuery` execution flow, raw-row execution, stage row counts, and chart telemetry paths were split into new helper class `SqlLikeExecutionFlowSupport`.
  - `SqlLikeParser` tokenization internals were split into new helper class `SqlLikeTokenizationSupport` (tokens, token types, position map, tokenizer).
  - size reductions now at `SqlLikeQuery`: `1254 -> 748`, `SqlLikeParser`: `1292 -> 1070`, with public API behavior preserved by regression suites.

## Next Actions

- Pre-adoption simplification roadmap is complete; no queued engineering work
  remains in `TODO.md`.
- Retry release workflow for `v1.0.0` (or manual dispatch) and confirm Central publish status for runtime + Boot starter artifacts.
- Keep lint baseline stable by reducing inherited violations incrementally and refreshing baseline only when intentional.
