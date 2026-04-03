package laughing.man.commits;

import laughing.man.commits.natural.NaturalQuery;
import laughing.man.commits.natural.NaturalTemplate;

/**
 * Controlled plain-English parser entry points.
 */
public final class PojoLensNatural {

    private PojoLensNatural() {
    }

    public static NaturalQuery parse(String naturalQuery) {
        return NaturalQuery.of(naturalQuery);
    }

    public static NaturalTemplate template(String naturalQuery, String... expectedParams) {
        return NaturalTemplate.of(naturalQuery, expectedParams);
    }
}
