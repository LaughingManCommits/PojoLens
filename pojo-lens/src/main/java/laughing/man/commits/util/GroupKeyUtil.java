package laughing.man.commits.util;

public final class GroupKeyUtil {

    public static final String NULL_GROUP_KEY = "<NULL>";

    private GroupKeyUtil() {
    }

    /**
     * Grouping identity for a value: the value itself (compared by {@code equals}), so
     * {@code null}, {@code ""}, distinct instants, and {@code LocalDate} values never merge.
     * Only when the caller set an explicit group date format are date-like values grouped by
     * their formatted text, which is how a coarser date grouping is requested.
     */
    public static Object groupKey(Object value, String explicitDateFormat) {
        if (explicitDateFormat != null && value != null && ObjectUtil.isDateLike(value)) {
            return ObjectUtil.castToString(value, explicitDateFormat);
        }
        return value;
    }

    public static String toGroupKeyValue(Object rawValue, String dateFormat) {
        if (rawValue == null) {
            return NULL_GROUP_KEY;
        }
        if (rawValue instanceof String value) {
            return StringUtil.isNull(value) ? NULL_GROUP_KEY : value;
        }
        String value = ObjectUtil.castToString(rawValue, dateFormat);
        return StringUtil.isNull(value) ? NULL_GROUP_KEY : value;
    }
}
