package laughing.man.commits.sqllike.internal.error;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlLikeFieldMessagesTest {

    @Test
    public void unknownFieldShouldIncludeSuggestionAndAllowedFields() {
        String message = SqlLikeFieldMessages.unknownField(
                "salry",
                "WHERE",
                new LinkedHashSet<>(Set.of("salary", "state"))
        );

        assertTrue(message.contains("Unknown field 'salry' in WHERE clause."));
        assertTrue(message.contains("Did you mean 'salary'?"));
        assertTrue(message.contains("Allowed fields:"));
    }

    @Test
    public void unknownFieldIfAllowedKnownShouldOmitAllowedFieldsWhenEmpty() {
        String message = SqlLikeFieldMessages.unknownFieldIfAllowedKnown("missing", "WHERE", Set.of());

        assertTrue(message.contains("Unknown field 'missing' in WHERE clause."));
        assertFalse(message.contains("Allowed fields:"));
    }

    @Test
    public void unknownFieldShouldKeepAllowedFieldsWhenEmpty() {
        String message = SqlLikeFieldMessages.unknownField("missing", "JOIN", Set.of());

        assertTrue(message.contains("Unknown field 'missing' in JOIN clause."));
        assertTrue(message.contains("Allowed fields: []"));
    }
}
