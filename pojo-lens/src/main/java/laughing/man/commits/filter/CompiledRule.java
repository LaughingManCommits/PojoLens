package laughing.man.commits.filter;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;

final class CompiledRule {
    final Object compareValue;
    final Clauses clause;
    final Separator separator;
    final String dateFormat;

    CompiledRule(Object compareValue, Clauses clause, Separator separator, String dateFormat) {
        this.compareValue = compareValue;
        this.clause = clause;
        this.separator = separator;
        this.dateFormat = dateFormat;
    }
}

