package laughing.man.commits.filter;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;

record CompiledRule(Object compareValue, Clauses clause, Separator separator, String dateFormat) {
}

