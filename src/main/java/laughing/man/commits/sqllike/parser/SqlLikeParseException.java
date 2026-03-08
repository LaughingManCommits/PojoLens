package laughing.man.commits.sqllike.parser;

import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;

public final class SqlLikeParseException extends IllegalArgumentException {

    private final String code;
    private final int index;
    private final int line;
    private final int column;
    private final String clause;
    private final int clauseStart;
    private final int clauseEnd;
    private final String snippet;

    public SqlLikeParseException(String message, int index, int line, int column) {
        this(SqlLikeErrorCodes.PARSE_SYNTAX, message, index, line, column, "QUERY", 0, index, null);
    }

    public SqlLikeParseException(String code, String message, int index, int line, int column) {
        this(code, message, index, line, column, "QUERY", 0, index, null);
    }

    public SqlLikeParseException(String message,
                                 int index,
                                 int line,
                                 int column,
                                 String clause,
                                 int clauseStart,
                                 int clauseEnd,
                                 String snippet) {
        this(SqlLikeErrorCodes.PARSE_SYNTAX, message, index, line, column, clause, clauseStart, clauseEnd, snippet);
    }

    public SqlLikeParseException(String code,
                                 String message,
                                 int index,
                                 int line,
                                 int column,
                                 String clause,
                                 int clauseStart,
                                 int clauseEnd,
                                 String snippet) {
        super(buildMessage(code, message, index, line, column, clause, clauseStart, clauseEnd, snippet));
        this.code = code;
        this.index = index;
        this.line = line;
        this.column = column;
        this.clause = clause;
        this.clauseStart = clauseStart;
        this.clauseEnd = clauseEnd;
        this.snippet = snippet;
    }

    public String code() {
        return code;
    }

    public int position() {
        return index;
    }

    public int index() {
        return index;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    public String clause() {
        return clause;
    }

    public int clauseStart() {
        return clauseStart;
    }

    public int clauseEnd() {
        return clauseEnd;
    }

    public String snippet() {
        return snippet;
    }

    private static String buildMessage(String code,
                                       String message,
                                       int index,
                                       int line,
                                       int column,
                                       String clause,
                                       int clauseStart,
                                       int clauseEnd,
                                       String snippet) {
        StringBuilder sb = new StringBuilder();
        sb.append(SqlLikeErrors.format(
                code,
                message
                        + " in "
                        + clause
                        + " clause"
                        + " at line "
                        + line
                        + ", column "
                        + column
                        + " (index "
                        + index
                        + ", span "
                        + clauseStart
                        + ".."
                        + clauseEnd
                        + ")"
        ));
        if (snippet != null && !snippet.isEmpty()) {
            sb.append(System.lineSeparator()).append(snippet);
        }
        return sb.toString();
    }
}

