package laughing.man.commits.natural;

import laughing.man.commits.chart.ChartData;
import laughing.man.commits.chart.ChartSpec;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Bound execution contract for natural queries.
 *
 * @param <T> projection type
 */
public interface NaturalBoundQuery<T> {

    List<T> filter();

    Iterator<T> iterator();

    Stream<T> stream();

    ChartData chart();

    ChartData chart(ChartSpec spec);
}
