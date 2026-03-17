package laughing.man.commits.filter;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;

final class CompiledRule {
    private final Object compareValue;
    private final Clauses clause;
    private final Separator separator;
    private final String dateFormat;

    CompiledRule(Object compareValue, Clauses clause, Separator separator, String dateFormat) {
        this.compareValue = compareValue;
        this.clause = clause;
        this.separator = separator;
        this.dateFormat = dateFormat;
    }

    Object getCompareValue() {
        return compareValue;
    }

    Clauses getClause() {
        return clause;
    }

    Separator getSeparator() {
        return separator;
    }

    String getDateFormat() {
        return dateFormat;
    }
}

