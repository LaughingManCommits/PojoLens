package laughing.man.commits.internal;

/**
 * Lowers caller text (a literal, or a SQL {@code LIKE} pattern) to the regex a
 * {@code MATCHES} / {@code NOT_MATCHES} rule evaluates. Pattern parameters keep the raw
 * bound value and lower it through this at execution time.
 */
public interface TextPattern {

    String toRegex(String value);
}
