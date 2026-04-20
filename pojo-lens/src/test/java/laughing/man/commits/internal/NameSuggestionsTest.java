package laughing.man.commits.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NameSuggestionsTest {

    @Test
    public void typoWithOneCloseMatchReturnsSingleSuggestion() {
        List<String> suggestions = NameSuggestions.suggest("salaery", Set.of("salary", "department", "name"));
        assertEquals(List.of("salary"), suggestions);
    }

    @Test
    public void typoWithMultipleCloseMatchesReturnsSortedSuggestions() {
        List<String> suggestions = NameSuggestions.suggest("stte", Set.of("state", "stage", "status", "name"));
        assertTrue(suggestions.size() > 1);
        assertTrue(suggestions.contains("state"));
        assertTrue(suggestions.contains("stage"));
    }

    @Test
    public void noCloseMatchReturnsEmptyList() {
        List<String> suggestions = NameSuggestions.suggest("qzxv", Set.of("salary", "department", "name"));
        assertTrue(suggestions.isEmpty());
    }

    @Test
    public void caseDifferenceIsTreatedAsMatch() {
        List<String> suggestions = NameSuggestions.suggest("SALARY", Set.of("salary", "department"));
        assertEquals(List.of("salary"), suggestions);
    }

    @Test
    public void mixedCaseTypoIsNormalized() {
        List<String> suggestions = NameSuggestions.suggest("SaLaEry", Set.of("salary", "department"));
        assertEquals(List.of("salary"), suggestions);
    }

    @Test
    public void prefixMatchIsIncluded() {
        List<String> suggestions = NameSuggestions.suggest("sal", Set.of("salary", "department"));
        assertTrue(suggestions.contains("salary"));
    }

    @Test
    public void nullUnknownReturnsEmptyList() {
        List<String> suggestions = NameSuggestions.suggest(null, Set.of("salary"));
        assertTrue(suggestions.isEmpty());
    }

    @Test
    public void blankUnknownReturnsEmptyList() {
        List<String> suggestions = NameSuggestions.suggest("   ", Set.of("salary"));
        assertTrue(suggestions.isEmpty());
    }

    @Test
    public void emptyCandidatesReturnsEmptyList() {
        List<String> suggestions = NameSuggestions.suggest("salary", Set.of());
        assertTrue(suggestions.isEmpty());
    }

    @Test
    public void atMostThreeSuggestionsReturned() {
        List<String> suggestions = NameSuggestions.suggest("stte",
                Set.of("state", "stage", "statue", "stare", "store"));
        assertTrue(suggestions.size() <= 3);
    }

    @Test
    public void formatFragmentEmptyReturnsEmptyString() {
        assertEquals("", NameSuggestions.formatFragment(List.of()));
    }

    @Test
    public void formatFragmentSingleSuggestion() {
        String fragment = NameSuggestions.formatFragment(List.of("salary"));
        assertEquals(" Did you mean 'salary'?", fragment);
    }

    @Test
    public void formatFragmentMultipleSuggestions() {
        String fragment = NameSuggestions.formatFragment(List.of("state", "stage"));
        assertTrue(fragment.contains("Did you mean one of"));
        assertTrue(fragment.contains("state"));
        assertTrue(fragment.contains("stage"));
    }
}
