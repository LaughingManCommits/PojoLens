package laughing.man.commits.filter;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;
import laughing.man.commits.enums.Sort;
import java.util.List;
import java.util.Map;

/**
 * Filter execution contract.
 *
 * Implementations should execute {@code filter(...)} and
 * {@code filterGroups(...)} against immutable execution snapshots so callers
 * can safely reuse configured builders. {@code join()} updates pipeline state
 * and should be treated as a state transition.
 */
public interface Filter {

    /**
     * Runs the configured query and returns grouped results.
     *
     * @param cls output type
     * @return grouped rows by configured group key
     * @throws IllegalStateException when query execution fails
     */
    <T extends Object> Map<String, List<T>> filterGroups(Class<T> cls);

    /**
     * Runs the configured query and returns flat results.
     *
     * @param cls output type
     * @return query results
     * @throws IllegalStateException when query execution fails
     */
    <T extends Object> List<T> filter(Class<T> cls);

    /**
     * Runs the configured query and returns flat results with sorting.
     *
     * @param sortMethod sort direction
     * @param cls output type
     * @return query results
     * @throws IllegalStateException when query execution fails
     */
    <T extends Object> List<T> filter(Sort sortMethod, Class<T> cls);

    /**
     * Runs the configured query and maps results to chart data payload.
     *
     * @param cls output row type used by query execution
     * @param spec chart mapping specification
     * @param <T> output row type
     * @return chart payload
     */
    <T extends Object> ChartData chart(Class<T> cls, ChartSpec spec);

    /**
     * Runs the configured query with sort and maps results to chart data payload.
     *
     * @param sortMethod sort direction
     * @param cls output row type used by query execution
     * @param spec chart mapping specification
     * @param <T> output row type
     * @return chart payload
     */
    <T extends Object> ChartData chart(Sort sortMethod, Class<T> cls, ChartSpec spec);

    /**
     * Applies configured joins and updates this executor state.
     *
     * @return current filter
     * @throws IllegalStateException when join execution fails
     */
    Filter join();
}

