package laughing.man.commits.internal;

import laughing.man.commits.enums.Join;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Single owner of joined-row column naming: the driving side keeps its names, and a
 * joined column whose name is taken becomes {@code child_<name>} (then
 * {@code child_<name>_1}, ...). A {@code RIGHT JOIN} drives from the joined rows, so
 * their columns come first and the existing columns are the ones renamed.
 */
public final class JoinFieldNames {

    private static final String CHILD_PREFIX = "child_";

    private JoinFieldNames() {
    }

    /**
     * The name a joined column gets when {@code baseName} is already in {@code used}.
     */
    public static String uniqueChildName(String baseName, Set<String> used) {
        String candidate = CHILD_PREFIX + baseName;
        int index = 1;
        while (used.contains(candidate)) {
            candidate = CHILD_PREFIX + baseName + "_" + index;
            index++;
        }
        return candidate;
    }

    /**
     * Column types after joining rows typed {@code joinedTypes} onto rows typed
     * {@code currentTypes} with {@code joinMethod}, in joined-row column order.
     */
    public static Map<String, Class<?>> merge(Map<String, Class<?>> currentTypes,
                                              Map<String, Class<?>> joinedTypes,
                                              Join joinMethod) {
        boolean right = Join.RIGHT_JOIN.equals(joinMethod);
        Map<String, Class<?>> driving = right ? joinedTypes : currentTypes;
        Map<String, Class<?>> other = right ? currentTypes : joinedTypes;
        LinkedHashMap<String, Class<?>> merged = new LinkedHashMap<>(driving);
        LinkedHashSet<String> used = new LinkedHashSet<>(driving.keySet());
        for (Map.Entry<String, Class<?>> entry : other.entrySet()) {
            String name = used.contains(entry.getKey()) ? uniqueChildName(entry.getKey(), used) : entry.getKey();
            merged.put(name, entry.getValue());
            used.add(name);
        }
        return merged;
    }
}
