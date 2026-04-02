package laughing.man.commits;

import laughing.man.commits.natural.NaturalQuery;

/**
 * Controlled plain-English parser entry points.
 */
public final class PojoLensNatural {

    private PojoLensNatural() {
    }

    public static NaturalQuery parse(String naturalQuery) {
        return NaturalQuery.of(naturalQuery);
    }
}
