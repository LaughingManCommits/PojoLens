package laughing.man.commits.benchmark;

import laughing.man.commits.builder.FilterQueryBuilder;
import laughing.man.commits.computed.ComputedFieldRegistry;
import laughing.man.commits.domain.QueryField;
import laughing.man.commits.domain.QueryRow;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Join;
import laughing.man.commits.enums.Metric;
import laughing.man.commits.enums.Separator;
import laughing.man.commits.filter.FilterCore;
import laughing.man.commits.filter.FilterExecutionPlanCache;
import laughing.man.commits.filter.FilterExecutionPlan;
import laughing.man.commits.filter.FilterExecutionPlanCacheKey;
import laughing.man.commits.filter.FilterExecutionPlanCacheStore;
import laughing.man.commits.filter.FastStatsQuerySupport;
import laughing.man.commits.sqllike.ast.QueryAst;
import laughing.man.commits.sqllike.internal.binding.SqlLikeBinder;
import laughing.man.commits.sqllike.internal.validation.SqlLikeValidator;
import laughing.man.commits.sqllike.parser.SqlLikeParser;
import laughing.man.commits.util.ReflectionUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class HotspotMicroJmhBenchmark {

    @Benchmark
    public List<QueryRow> reflectionToDomainRows(FlattenState state) {
        return ReflectionUtil.toDomainRows(state.source);
    }

    @Benchmark
    public List<ProjectionTarget> reflectionToClassList(ProjectionState state) {
        return ReflectionUtil.toClassList(ProjectionTarget.class, state.rows);
    }

    @Benchmark
    public FilterExecutionPlan statsPlanCacheHit(StatsPlanCacheState state) {
        return state.executionPlanCache.getOrBuild(state.cacheKey, state.planBuilder);
    }

    @Benchmark
    public void sqlLikePreparedStatsRebindCopy(PreparedSqlLikeStatsState state, Blackhole blackhole) {
        FilterQueryBuilder builder = state.preparedTemplate.preparedExecutionCopy(state.source, state.joinSourcesByIndex);
        blackhole.consume(builder);
        blackhole.consume(builder.getSourceBeansForExecution().size());
    }

    @Benchmark
    public void sqlLikePreparedStatsRebindView(PreparedSqlLikeStatsState state, Blackhole blackhole) {
        FilterQueryBuilder builder = state.preparedTemplate.preparedExecutionView(state.source, state.joinSourcesByIndex);
        blackhole.consume(builder);
        blackhole.consume(builder.getSourceBeansForExecution().size());
    }

    @Benchmark
    public void sqlLikePreparedStatsFastPathSetupCopy(PreparedSqlLikeStatsState state, Blackhole blackhole) {
        FilterQueryBuilder builder = state.preparedTemplate.preparedExecutionCopy(state.source, state.joinSourcesByIndex);
        FastStatsQuerySupport.FastStatsState fastStatsState =
                FastStatsQuerySupport.tryBuildState(builder, state.rawExecutionPlanCacheKey);
        blackhole.consume(fastStatsState);
        blackhole.consume(fastStatsState == null ? 0 : fastStatsState.rows().size());
    }

    @Benchmark
    public void sqlLikePreparedStatsFastPathSetupView(PreparedSqlLikeStatsState state, Blackhole blackhole) {
        FilterQueryBuilder builder = state.preparedTemplate.preparedExecutionView(state.source, state.joinSourcesByIndex);
        FastStatsQuerySupport.FastStatsState fastStatsState =
                FastStatsQuerySupport.tryBuildState(builder, state.rawExecutionPlanCacheKey);
        blackhole.consume(fastStatsState);
        blackhole.consume(fastStatsState == null ? 0 : fastStatsState.rows().size());
    }

    @Benchmark
    public List<QueryRow> groupedMultiMetricAggregation(AggregationState state) {
        return state.core.aggregateMetrics(state.rows, state.plan);
    }

    @Benchmark
    public int computedFieldJoinSelectiveMaterialization(ComputedFieldJoinState state) {
        FilterQueryBuilder builder = new FilterQueryBuilder(state.parents)
                .computedFields(state.registry)
                .addJoinBeans("id", state.children, "parentId", Join.LEFT_JOIN)
                .addRule("totalComp", state.minimumTotalComp, Clauses.BIGGER_EQUAL, Separator.AND)
                .addField("name")
                .addField("totalComp");

        List<QueryRow> parentRows = builder.getRows();
        List<QueryRow> childRows = builder.getJoinClassesForExecution().get(1);
        return parentRows.size() + (childRows == null ? 0 : childRows.size());
    }

    @State(Scope.Thread)
    public static class FlattenState {

        @Param({"1000", "10000"})
        public int size;

        private List<FlattenEmployee> source;

        @Setup(Level.Trial)
        public void setup() {
            source = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                source.add(new FlattenEmployee(
                        "emp" + i,
                        new Date(BenchmarkProfiles.BASE_EPOCH_MILLIS + (i * 60_000L)),
                        BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 701L, i, 250_000),
                        new FlattenDepartment(
                                "dept" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 702L, i, 24),
                                "region" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 703L, i, 6)
                        ),
                        new FlattenManager(
                                "mgr" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 704L, i, 64),
                                BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 705L, i, 8)
                        )
                ));
            }

            ReflectionUtil.toDomainRows(List.of(source.get(0)));
        }
    }

    @State(Scope.Thread)
    public static class ProjectionState {

        @Param({"1000", "10000"})
        public int size;

        private List<QueryRow> rows;

        @Setup(Level.Trial)
        public void setup() {
            rows = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                rows.add(projectionRow(i));
            }

            ReflectionUtil.toClassList(ProjectionTarget.class, List.of(rows.get(0)));
        }
    }

    @State(Scope.Thread)
    public static class StatsPlanCacheState {

        @Param({"1000", "10000"})
        public int size;

        private FilterExecutionPlanCacheStore executionPlanCache;
        private FilterExecutionPlanCacheKey cacheKey;
        private Supplier<FilterExecutionPlan> planBuilder;

        @Setup(Level.Trial)
        public void setup() {
            executionPlanCache = new FilterExecutionPlanCacheStore();
            FilterQueryBuilder builder = new FilterQueryBuilder(statsSource(size), executionPlanCache)
                    .addRule("integerField", 200, Clauses.BIGGER_EQUAL, Separator.AND)
                    .addDistinct("stringField")
                    .addOrder("integerField", 1)
                    .addGroup("stringField")
                    .addField("stringField")
                    .addCount("rowCount")
                    .addMetric("integerField", Metric.SUM, "sumValue")
                    .addMetric("integerField", Metric.AVG, "avgValue");

            FilterCore core = new FilterCore(builder);
            cacheKey = FilterExecutionPlanCacheKey.from(builder);
            planBuilder = core::buildExecutionPlan;

            executionPlanCache.getOrBuild(cacheKey, planBuilder);
        }
    }

    @State(Scope.Thread)
    public static class PreparedSqlLikeStatsState {

        @Param({"1000", "10000"})
        public int size;

        private List<BenchmarkFoo> source;
        private Map<Integer, List<?>> joinSourcesByIndex;
        private FilterQueryBuilder preparedTemplate;
        private FilterExecutionPlanCacheKey rawExecutionPlanCacheKey;

        @Setup(Level.Trial)
        public void setup() {
            source = statsSource(size);
            joinSourcesByIndex = Map.of();

            FilterExecutionPlanCache.setEnabled(true);
            FilterExecutionPlanCache.setStatsEnabled(true);
            FilterExecutionPlanCache.setMaxEntries(1024);
            FilterExecutionPlanCache.setMaxWeight(0L);
            FilterExecutionPlanCache.setExpireAfterWriteMillis(0L);
            FilterExecutionPlanCache.clear();
            FilterExecutionPlanCache.resetStats();

            QueryAst ast = SqlLikeParser.parse(
                    "select bucket(dateField,'month') as period, count(*) as total, sum(integerField) as totalValue "
                            + "group by period"
            );
            QueryAst normalizedAst = SqlLikeValidator.validateForFilter(
                    ast,
                    BenchmarkFoo.class,
                    PreparedStatsRow.class,
                    Map.of(),
                    false,
                    ComputedFieldRegistry.empty()
            );
            FilterQueryBuilder boundBuilder = (FilterQueryBuilder) SqlLikeBinder.bind(
                    normalizedAst,
                    source,
                    Map.of(),
                    BenchmarkFoo.class,
                    ComputedFieldRegistry.empty()
            );
            preparedTemplate = boundBuilder.snapshotForPreparedExecution();
            rawExecutionPlanCacheKey = FilterExecutionPlanCacheKey.from(boundBuilder);

            FilterQueryBuilder warmedView = preparedTemplate.preparedExecutionView(source, joinSourcesByIndex);
            FastStatsQuerySupport.tryBuildState(warmedView, rawExecutionPlanCacheKey);
            FilterQueryBuilder warmedCopy = preparedTemplate.preparedExecutionCopy(source, joinSourcesByIndex);
            FastStatsQuerySupport.tryBuildState(warmedCopy, rawExecutionPlanCacheKey);
        }
    }

    @State(Scope.Thread)
    public static class AggregationState {

        @Param({"1000", "10000"})
        public int size;

        private FilterCore core;
        private FilterExecutionPlan plan;
        private List<QueryRow> rows;

        @Setup(Level.Trial)
        public void setup() {
            FilterQueryBuilder builder = new FilterQueryBuilder(statsSource(size))
                    .addGroup("stringField")
                    .addCount("rowCount")
                    .addMetric("integerField", Metric.SUM, "sumValue")
                    .addMetric("integerField", Metric.AVG, "avgValue")
                    .addMetric("integerField", Metric.MIN, "minValue")
                    .addMetric("integerField", Metric.MAX, "maxValue");

            core = new FilterCore(builder);
            plan = core.buildExecutionPlan();
            rows = core.getBuilder().getRows();
        }
    }

    @State(Scope.Thread)
    public static class ComputedFieldJoinState {

        @Param({"1000", "10000"})
        public int size;

        private List<ComputedFieldParent> parents;
        private List<ComputedFieldChild> children;
        private ComputedFieldRegistry registry;
        private double minimumTotalComp;

        @Setup(Level.Trial)
        public void setup() {
            parents = new ArrayList<>(size);
            children = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int salary = 90_000 + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1001L, i, 70_000);
                int bonus = 5_000 + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1002L, i, 20_000);
                parents.add(new ComputedFieldParent(
                        i,
                        "emp" + i,
                        salary,
                        "dept" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1003L, i, 24)
                ));
                children.add(new ComputedFieldChild(
                        i,
                        bonus,
                        "tag" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 1004L, i, 8)
                ));
            }
            registry = ComputedFieldRegistry.builder()
                    .add("totalComp", "salary + bonus", Double.class)
                    .build();
            minimumTotalComp = 140_000d;
        }
    }

    private static List<BenchmarkFoo> statsSource(int size) {
        ArrayList<BenchmarkFoo> source = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            source.add(new BenchmarkFoo(
                    "dept" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 801L, i, 24),
                    new Date(BenchmarkProfiles.STATS_BASE_EPOCH_MILLIS + (i * 86_400_000L)),
                    BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 802L, i, 1_000)
            ));
        }
        return source;
    }

    private static QueryRow projectionRow(int index) {
        ArrayList<QueryField> fields = new ArrayList<>(6);
        fields.add(queryField(
                "department.name",
                "dept" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 901L, index, 24)));
        fields.add(queryField(
                "department.region",
                "region" + BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 902L, index, 6)));
        fields.add(queryField(
                "totals.rowCount",
                (long) (BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 903L, index, 400) + 1)));
        fields.add(queryField(
                "totals.avgValue",
                BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 904L, index, 2_000) / 10.0d));
        fields.add(queryField("rank", BenchmarkProfiles.deterministicInt(BenchmarkProfiles.DATA_SEED + 905L, index, 100)));
        fields.add(queryField("snapshotDate", new Date(BenchmarkProfiles.STATS_BASE_EPOCH_MILLIS + (index * 86_400_000L))));

        QueryRow row = new QueryRow();
        row.setFields(fields);
        return row;
    }

    private static QueryField queryField(String fieldName, Object value) {
        QueryField field = new QueryField();
        field.setFieldName(fieldName);
        field.setValue(value);
        return field;
    }

    public static class FlattenEmployee {
        String employeeId;
        Date createdAt;
        int salary;
        FlattenDepartment department;
        FlattenManager manager;

        public FlattenEmployee() {
        }

        public FlattenEmployee(String employeeId,
                               Date createdAt,
                               int salary,
                               FlattenDepartment department,
                               FlattenManager manager) {
            this.employeeId = employeeId;
            this.createdAt = createdAt;
            this.salary = salary;
            this.department = department;
            this.manager = manager;
        }
    }

    public static class FlattenDepartment {
        String name;
        String region;

        public FlattenDepartment() {
        }

        public FlattenDepartment(String name, String region) {
            this.name = name;
            this.region = region;
        }
    }

    public static class FlattenManager {
        String name;
        int level;

        public FlattenManager() {
        }

        public FlattenManager(String name, int level) {
            this.name = name;
            this.level = level;
        }
    }

    public static class ProjectionTarget {
        ProjectionDepartment department;
        ProjectionTotals totals;
        int rank;
        Date snapshotDate;

        public ProjectionTarget() {
        }
    }

    public static class ProjectionDepartment {
        String name;
        String region;

        public ProjectionDepartment() {
        }
    }

    public static class ProjectionTotals {
        long rowCount;
        double avgValue;

        public ProjectionTotals() {
        }
    }

    public static class PreparedStatsRow {
        String period;
        long total;
        long totalValue;

        public PreparedStatsRow() {
        }
    }

    public static class ComputedFieldParent {
        int id;
        String name;
        int salary;
        String department;

        public ComputedFieldParent() {
        }

        public ComputedFieldParent(int id, String name, int salary, String department) {
            this.id = id;
            this.name = name;
            this.salary = salary;
            this.department = department;
        }
    }

    public static class ComputedFieldChild {
        int parentId;
        int bonus;
        String tag;

        public ComputedFieldChild() {
        }

        public ComputedFieldChild(int parentId, int bonus, String tag) {
            this.parentId = parentId;
            this.bonus = bonus;
            this.tag = tag;
        }
    }
}
