package laughing.man.commits;

import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.sqllike.SqlLikeTemplate;
import laughing.man.commits.sqllike.internal.cache.DefaultSqlLikeQueryCacheSupport;

/**
 * SQL-like parser/cache entry points.
 */
public final class PojoLensSql {

    private PojoLensSql() {
    }

    public static SqlLikeQuery parse(String sqlLikeQuery) {
        return DefaultSqlLikeQueryCacheSupport.parse(sqlLikeQuery);
    }

    public static SqlLikeTemplate template(String sqlLikeQuery, String... expectedParams) {
        return SqlLikeTemplate.of(parse(sqlLikeQuery), expectedParams);
    }
}

