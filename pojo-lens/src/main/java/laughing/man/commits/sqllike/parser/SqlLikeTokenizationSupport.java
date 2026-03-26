package laughing.man.commits.sqllike.parser;

import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SqlLikeTokenizationSupport {

    private SqlLikeTokenizationSupport() {
    }

    static List<Token> tokenize(String value,
                                PositionMap positionMap,
                                Set<String> keywords,
                                int maxTokens) {
        List<Token> output = new ArrayList<>();
        int i = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch)) {
                i++;
                continue;
            }
            if (ch == ',') {
                addToken(output, maxTokens, new Token(TokenType.COMMA, ",", i++, positionMap.lineOf(i - 1), positionMap.columnOf(i - 1)), value, positionMap);
                continue;
            }
            if (ch == '(') {
                addToken(output, maxTokens, new Token(TokenType.LEFT_PAREN, "(", i++, positionMap.lineOf(i - 1), positionMap.columnOf(i - 1)), value, positionMap);
                continue;
            }
            if (ch == ')') {
                addToken(output, maxTokens, new Token(TokenType.RIGHT_PAREN, ")", i++, positionMap.lineOf(i - 1), positionMap.columnOf(i - 1)), value, positionMap);
                continue;
            }
            if (ch == '*') {
                addToken(output, maxTokens, new Token(TokenType.STAR, "*", i++, positionMap.lineOf(i - 1), positionMap.columnOf(i - 1)), value, positionMap);
                continue;
            }
            if (ch == '\'') {
                int start = i++;
                StringBuilder sb = new StringBuilder();
                boolean terminated = false;
                while (i < value.length()) {
                    char c = value.charAt(i);
                    if (c == '\'') {
                        if (i + 1 < value.length() && value.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                            continue;
                        }
                        i++;
                        terminated = true;
                        break;
                    }
                    sb.append(c);
                    i++;
                }
                if (!terminated) {
                    throw syntaxError(value, positionMap, "Unterminated string literal", start);
                }
                addToken(output, maxTokens, new Token(TokenType.STRING, sb.toString(), start, positionMap.lineOf(start), positionMap.columnOf(start)), value, positionMap);
                continue;
            }
            if (ch == ':') {
                int start = i++;
                if (i >= value.length() || !isIdentifierStart(value.charAt(i))) {
                    throw syntaxError(value, positionMap, "Expected parameter name after ':'", start);
                }
                while (i < value.length() && isIdentifierPart(value.charAt(i))) {
                    i++;
                }
                addToken(output, maxTokens, new Token(
                        TokenType.PARAM,
                        value.substring(start, i),
                        start,
                        positionMap.lineOf(start),
                        positionMap.columnOf(start)
                ), value, positionMap);
                continue;
            }
            if (ch == '!' || ch == '<' || ch == '>' || ch == '=' || ch == '+' || ch == '/' || ch == '-') {
                int start = i;
                if (ch == '-' && i + 1 < value.length() && Character.isDigit(value.charAt(i + 1))) {
                    i++;
                    while (i < value.length() && Character.isDigit(value.charAt(i))) {
                        i++;
                    }
                    if (i < value.length() && value.charAt(i) == '.') {
                        i++;
                        if (i >= value.length() || !Character.isDigit(value.charAt(i))) {
                            throw syntaxError(value, positionMap, "Invalid decimal number", i);
                        }
                        while (i < value.length() && Character.isDigit(value.charAt(i))) {
                            i++;
                        }
                    }
                    addToken(output, maxTokens, new Token(TokenType.NUMBER, value.substring(start, i), start, positionMap.lineOf(start), positionMap.columnOf(start)), value, positionMap);
                    continue;
                }
                if (i + 1 < value.length()) {
                    String two = value.substring(i, i + 2);
                    if ("!=".equals(two) || ">=".equals(two) || "<=".equals(two) || "<>".equals(two)) {
                        addToken(output, maxTokens, new Token(TokenType.OPERATOR, two, start, positionMap.lineOf(start), positionMap.columnOf(start)), value, positionMap);
                        i += 2;
                        continue;
                    }
                }
                addToken(output, maxTokens, new Token(TokenType.OPERATOR, String.valueOf(ch), start, positionMap.lineOf(start), positionMap.columnOf(start)), value, positionMap);
                i++;
                continue;
            }
            if (Character.isDigit(ch)) {
                int start = i;
                i++;
                while (i < value.length() && Character.isDigit(value.charAt(i))) {
                    i++;
                }
                if (i < value.length() && value.charAt(i) == '.') {
                    i++;
                    if (i >= value.length() || !Character.isDigit(value.charAt(i))) {
                        throw syntaxError(value, positionMap, "Invalid decimal number", i);
                    }
                    while (i < value.length() && Character.isDigit(value.charAt(i))) {
                        i++;
                    }
                }
                addToken(output, maxTokens, new Token(TokenType.NUMBER, value.substring(start, i), start, positionMap.lineOf(start), positionMap.columnOf(start)), value, positionMap);
                continue;
            }
            if (isIdentifierStart(ch)) {
                int start = i++;
                while (i < value.length() && isIdentifierPart(value.charAt(i))) {
                    i++;
                }
                String word = value.substring(start, i);
                if (isKeyword(word, keywords)) {
                    addToken(output, maxTokens, new Token(TokenType.KEYWORD, word, start, positionMap.lineOf(start), positionMap.columnOf(start)), value, positionMap);
                } else {
                    addToken(output, maxTokens, new Token(TokenType.IDENTIFIER, word, start, positionMap.lineOf(start), positionMap.columnOf(start)), value, positionMap);
                }
                continue;
            }
            throw syntaxError(value, positionMap, "Unexpected character '" + ch + "'", i);
        }
        addToken(output, maxTokens, new Token(
                TokenType.EOF,
                "",
                value.length(),
                positionMap.lineOf(value.length()),
                positionMap.columnOf(value.length())
        ), value, positionMap);
        return output;
    }

    private static void addToken(List<Token> tokens,
                                 int maxTokens,
                                 Token token,
                                 String input,
                                 PositionMap positionMap) {
        if (tokens.size() >= maxTokens) {
            throw error(SqlLikeErrorCodes.PARSE_TOKEN_LIMIT,
                    input,
                    positionMap,
                    "Query exceeds maximum token count of " + maxTokens,
                    token.position);
        }
        tokens.add(token);
    }

    private static boolean isIdentifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private static boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '.';
    }

    private static boolean isKeyword(String word, Set<String> keywords) {
        return keywords.contains(word.toUpperCase(Locale.ROOT));
    }

    private static SqlLikeParseException syntaxError(String input,
                                                     PositionMap positionMap,
                                                     String message,
                                                     int position) {
        return error(SqlLikeErrorCodes.PARSE_SYNTAX, input, positionMap, message, position);
    }

    private static SqlLikeParseException error(String code,
                                               String input,
                                               PositionMap positionMap,
                                               String message,
                                               int position) {
        int safe = clamp(position, input.length());
        return new SqlLikeParseException(
                code,
                message,
                safe,
                positionMap.lineOf(safe),
                positionMap.columnOf(safe),
                "QUERY",
                0,
                input.length(),
                buildSnippet(input, safe)
        );
    }

    private static int clamp(int value, int inputLength) {
        return Math.max(0, Math.min(value, inputLength));
    }

    private static String buildSnippet(String input, int position) {
        int lineStart = position;
        while (lineStart > 0 && input.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        int lineEnd = position;
        while (lineEnd < input.length() && input.charAt(lineEnd) != '\n') {
            lineEnd++;
        }
        String line = input.substring(lineStart, lineEnd);
        StringBuilder caret = new StringBuilder();
        for (int i = lineStart; i < position; i++) {
            caret.append(input.charAt(i) == '\t' ? '\t' : ' ');
        }
        caret.append('^');
        return line + System.lineSeparator() + caret;
    }

    enum TokenType {
        IDENTIFIER,
        KEYWORD,
        STRING,
        NUMBER,
        PARAM,
        OPERATOR,
        COMMA,
        LEFT_PAREN,
        RIGHT_PAREN,
        STAR,
        EOF
    }

    static final class Token {
        final TokenType type;
        final String text;
        final int position;
        final int line;
        final int column;

        Token(TokenType type, String text, int position, int line, int column) {
            this.type = type;
            this.text = text;
            this.position = position;
            this.line = line;
            this.column = column;
        }
    }

    static final class PositionMap {
        private final int[] lines;
        private final int[] columns;

        private PositionMap(int[] lines, int[] columns) {
            this.lines = lines;
            this.columns = columns;
        }

        static PositionMap build(String input) {
            int[] lines = new int[input.length() + 1];
            int[] columns = new int[input.length() + 1];
            int line = 1;
            int column = 1;
            for (int i = 0; i <= input.length(); i++) {
                lines[i] = line;
                columns[i] = column;
                if (i == input.length()) {
                    break;
                }
                char ch = input.charAt(i);
                if (ch == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new PositionMap(lines, columns);
        }

        int lineOf(int index) {
            int safe = Math.max(0, Math.min(index, lines.length - 1));
            return lines[safe];
        }

        int columnOf(int index) {
            int safe = Math.max(0, Math.min(index, columns.length - 1));
            return columns[safe];
        }
    }
}
