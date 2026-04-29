package laughing.man.commits.spring.boot.autoconfigure;

import laughing.man.commits.sqllike.SqlLikeResultSetAdapter;
import laughing.man.commits.sqllike.SqlLikePushdownResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Thin bridge between {@link JdbcTemplate} and {@link SqlLikeResultSetAdapter}.
 *
 * <p>Use {@link #query} to run a SQL query and materialize rows directly into a
 * PojoLens-compatible projection class. Use {@link #queryPushed} when you want
 * to record which query stages were handled by the database.
 *
 * <p>Requires {@code spring-jdbc} on the classpath.
 */
public final class PojoLensJdbc {

    private PojoLensJdbc() {
    }

    /**
     * Runs {@code sql} via {@code jdbcTemplate} and materializes the result into
     * {@code rowClass} using {@link SqlLikeResultSetAdapter}.
     */
    public static <T> List<T> query(JdbcTemplate jdbcTemplate,
                                    String sql,
                                    Class<T> rowClass,
                                    Object... params) {
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        Objects.requireNonNull(sql, "sql must not be null");
        Objects.requireNonNull(rowClass, "rowClass must not be null");
        ResultSetExtractor<List<T>> extractor = rs -> SqlLikeResultSetAdapter.read(rs, rowClass);
        return jdbcTemplate.query(sql, extractor, params);
    }

    /**
     * Runs {@code sql} and returns a {@link SqlLikePushdownResult} that records
     * which stages were pushed down to the database.
     */
    public static <T> SqlLikePushdownResult<T> queryPushed(JdbcTemplate jdbcTemplate,
                                                            String sql,
                                                            Class<T> rowClass,
                                                            Collection<String> pushedStages,
                                                            Object... params) {
        Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        Objects.requireNonNull(sql, "sql must not be null");
        Objects.requireNonNull(rowClass, "rowClass must not be null");
        ResultSetExtractor<SqlLikePushdownResult<T>> extractor =
                rs -> SqlLikeResultSetAdapter.readPushed(rs, rowClass, pushedStages);
        return jdbcTemplate.query(sql, extractor, params);
    }

    /**
     * Delegates to {@link SqlLikeResultSetAdapter#read(ResultSet, Class)}.
     * Useful when you hold a raw {@link ResultSet} outside of a template.
     */
    public static <T> List<T> read(ResultSet resultSet, Class<T> rowClass) {
        return SqlLikeResultSetAdapter.read(resultSet, rowClass);
    }
}
