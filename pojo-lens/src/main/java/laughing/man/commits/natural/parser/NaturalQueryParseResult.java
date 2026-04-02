package laughing.man.commits.natural.parser;

import laughing.man.commits.sqllike.ast.QueryAst;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Parsed natural query plus captured source-field phrases.
 */
public final class NaturalQueryParseResult {

    private final QueryAst ast;
    private final Map<String, String> sourceFieldPhrases;

    public NaturalQueryParseResult(QueryAst ast, Map<String, String> sourceFieldPhrases) {
        this.ast = Objects.requireNonNull(ast, "ast must not be null");
        this.sourceFieldPhrases = Collections.unmodifiableMap(new LinkedHashMap<>(
                sourceFieldPhrases == null ? Map.of() : sourceFieldPhrases
        ));
    }

    public QueryAst ast() {
        return ast;
    }

    public Map<String, String> sourceFieldPhrases() {
        return sourceFieldPhrases;
    }
}
