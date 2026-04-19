package laughing.man.commits.internal.builder;

import laughing.man.commits.internal.FluentEngine;
import laughing.man.commits.table.TabularSchema;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Immutable fluent-query definition that builds a fresh {@link QueryBuilder}
 * for each execution.
 *
 * @param <T> projection row type
 */
public final class FluentQueryDefinition<T> {

    private final Class<T> projectionClass;
    private final Consumer<QueryBuilder> configurer;
    private final TabularSchema schema;

    private FluentQueryDefinition(Class<T> projectionClass, Consumer<QueryBuilder> configurer) {
        this.projectionClass = Objects.requireNonNull(projectionClass, "projectionClass must not be null");
        this.configurer = Objects.requireNonNull(configurer, "configurer must not be null");
        this.schema = configuredBuilder(List.of()).schema(projectionClass);
    }

    public static <T> FluentQueryDefinition<T> of(Class<T> projectionClass, Consumer<QueryBuilder> configurer) {
        return new FluentQueryDefinition<>(projectionClass, configurer);
    }

    public String source() {
        return "fluent";
    }

    public Class<T> projectionClass() {
        return projectionClass;
    }

    public TabularSchema schema() {
        return schema;
    }

    public Map<String, Object> explain() {
        return configuredBuilder(List.of()).explain();
    }

    public List<T> rows(List<?> sourceRows) {
        Objects.requireNonNull(sourceRows, "sourceRows must not be null");
        return configuredBuilder(sourceRows).initFilter().filter(projectionClass);
    }

    private QueryBuilder configuredBuilder(List<?> sourceRows) {
        QueryBuilder builder = FluentEngine.newQueryBuilder(sourceRows);
        configurer.accept(builder);
        return builder;
    }
}
