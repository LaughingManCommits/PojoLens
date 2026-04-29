package laughing.man.commits.sqllike.internal.error;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SqlLikeSourceBindingMessagesTest {

    @Test
    public void missingJoinSourceBindingShouldIncludeSuggestionAndAvailableSources() {
        String message = SqlLikeSourceBindingMessages.missingJoinSourceBinding(
                "employes",
                new LinkedHashSet<>(Set.of("employees", "assignments"))
        );

        assertTrue(message.contains("Missing JOIN source binding for 'employes'"));
        assertTrue(message.contains("Did you mean 'employees'?"));
        assertTrue(message.contains("Available source binding(s):"));
    }

    @Test
    public void missingSubquerySourceBindingShouldIncludeSuggestionAndAvailableSources() {
        String message = SqlLikeSourceBindingMessages.missingSubquerySourceBinding(
                "employes",
                new LinkedHashSet<>(Set.of("employees"))
        );

        assertTrue(message.contains("Missing subquery source binding for 'employes'"));
        assertTrue(message.contains("Did you mean 'employees'?"));
        assertTrue(message.contains("Available source binding(s): [employees]"));
    }

    @Test
    public void missingJoinSourceBindingShouldOmitAvailableSourcesWhenEmpty() {
        String message = SqlLikeSourceBindingMessages.missingJoinSourceBinding("missing", Set.of());

        assertTrue(message.contains("Missing JOIN source binding for 'missing'"));
        assertFalse(message.contains("Available source binding(s):"));
    }
}

