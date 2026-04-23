package laughing.man.commits.sqllike;

import laughing.man.commits.util.ReflectionUtil;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JDBC {@link ResultSet} materialization helper for host-owned pushdown bridges.
 */
public final class SqlLikeResultSetAdapter {

    private SqlLikeResultSetAdapter() {
    }

    /**
     * Reads all rows from the current {@link ResultSet} into mutable PojoLens
     * projection objects using column labels as field names.
     *
     * @param resultSet result set positioned before the first row
     * @param rowClass destination row class
     * @param <T> row type
     * @return materialized rows
     */
    public static <T> List<T> read(ResultSet resultSet, Class<T> rowClass) {
        Objects.requireNonNull(resultSet, "resultSet must not be null");
        Objects.requireNonNull(rowClass, "rowClass must not be null");
        try {
            List<String> schema = schema(resultSet.getMetaData(), rowClass);
            List<Object[]> rows = rows(resultSet, schema.size());
            return ReflectionUtil.toClassList(rowClass, rows, schema);
        } catch (SQLException ex) {
            throw new SqlLikePushdownException("Failed to read pushed ResultSet rows", ex);
        }
    }

    /**
     * Reads all rows and wraps them as a pushdown result.
     *
     * @param resultSet result set positioned before the first row
     * @param rowClass destination row class
     * @param pushedStages stages performed by the host adapter
     * @param <T> row type
     * @return pushdown result
     */
    public static <T> SqlLikePushdownResult<T> readPushed(ResultSet resultSet,
                                                          Class<T> rowClass,
                                                          Collection<String> pushedStages) {
        List<T> rows = read(resultSet, rowClass);
        return SqlLikePushdownResult.of(
                rows,
                pushedStages == null ? List.of() : pushedStages,
                -1,
                Map.of("adapter", "resultSet")
        );
    }

    private static List<String> schema(ResultSetMetaData metadata, Class<?> rowClass) throws SQLException {
        Objects.requireNonNull(metadata, "metadata must not be null");
        int columnCount = metadata.getColumnCount();
        Set<String> fieldNames = new LinkedHashSet<>(ReflectionUtil.collectQueryableFieldNames(rowClass));
        LinkedHashSet<String> seenLabels = new LinkedHashSet<>();
        List<String> schema = new ArrayList<>(columnCount);
        for (int column = 1; column <= columnCount; column++) {
            String label = columnLabel(metadata, column);
            if (!seenLabels.add(label)) {
                throw new SqlLikePushdownException("Duplicate ResultSet column label '" + label + "'");
            }
            if (!fieldNames.contains(label)) {
                throw new SqlLikePushdownException("ResultSet column label '" + label
                        + "' does not match a queryable field on " + rowClass.getSimpleName());
            }
            schema.add(label);
        }
        return schema;
    }

    private static String columnLabel(ResultSetMetaData metadata, int column) throws SQLException {
        String label = metadata.getColumnLabel(column);
        if (label == null || label.isBlank()) {
            label = metadata.getColumnName(column);
        }
        if (label == null || label.isBlank()) {
            throw new SqlLikePushdownException("ResultSet column " + column + " has no column label");
        }
        return label.trim();
    }

    private static List<Object[]> rows(ResultSet resultSet, int columnCount) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        while (resultSet.next()) {
            Object[] values = new Object[columnCount];
            for (int column = 1; column <= columnCount; column++) {
                values[column - 1] = resultSet.getObject(column);
            }
            rows.add(values);
        }
        return rows;
    }
}
