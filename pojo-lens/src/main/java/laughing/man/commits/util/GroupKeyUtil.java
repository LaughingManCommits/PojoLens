package laughing.man.commits.util;

public final class GroupKeyUtil {

    public static final String NULL_GROUP_KEY = "<NULL>";

    private GroupKeyUtil() {
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
