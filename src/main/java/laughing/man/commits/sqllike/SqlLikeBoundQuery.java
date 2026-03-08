package laughing.man.commits.sqllike;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;

import java.util.List;

/**
 * Typed SQL-like bind contract that captures projection class and SQL sort
 * direction at bind-time so execution no longer needs duplicate parameters.
 *
 * @param <T> projection row type
 */
public interface SqlLikeBoundQuery<T> {

    /**
     * Executes query using bind-time projection and SQL-like ORDER BY.
     *
     * @return filtered rows
     */
    List<T> filter();

    /**
     * Executes query and maps rows to chart payload.
     *
     * @param spec chart mapping spec
     * @return chart payload
     */
    ChartData chart(ChartSpec spec);
}

