package laughing.man.commits.sqllike;

import laughing.man.commits.sqllike.internal.error.SqlLikeErrorCodes;
import laughing.man.commits.sqllike.internal.error.SqlLikeErrors;
import laughing.man.commits.util.StringUtil;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable keyset cursor payload for SQL-like paging.
 * <p>
 * Cursor values are keyed by field name and are matched against
 * {@code ORDER BY} fields when applied through {@link SqlLikeQuery}
 * keyset methods.
 */
public final class SqlLikeCursor {

    private static final String TOKEN_VERSION = "v1";
    private static final int CURSOR_ENTRY_PART_COUNT = 3;

    private final Map<String, Object> values;

    private SqlLikeCursor(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SqlLikeCursor of(Map<String, ?> values) {
        return builder().putAll(values).build();
    }

    public Map<String, Object> values() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    public String toToken() {
        if (values.isEmpty()) {
            throw cursor(SqlLikeErrorCodes.CURSOR_VALUE_INVALID,
                    "Cursor token cannot be generated from empty cursor");
        }
        StringBuilder raw = new StringBuilder(TOKEN_VERSION);
        values.forEach((field, value) -> {
            EncodedValue encoded = EncodedValue.encode(value);
            raw.append(';')
                    .append(encodeComponent(field))
                    .append(',')
                    .append(encoded.type())
                    .append(',')
                    .append(encodeComponent(encoded.value()));
        });
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static SqlLikeCursor fromToken(String token) {
        if (StringUtil.isNullOrBlank(token)) {
            throw cursor(SqlLikeErrorCodes.CURSOR_TOKEN_INVALID,
                    "Cursor token must not be null/blank");
        }
        final String decoded;
        try {
            byte[] raw = Base64.getUrlDecoder().decode(token);
            decoded = new String(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw cursor(SqlLikeErrorCodes.CURSOR_TOKEN_INVALID,
                    "Cursor token is not valid Base64URL");
        }

        String[] segments = decoded.split(";", -1);
        if (segments.length < 2 || !TOKEN_VERSION.equals(segments[0])) {
            throw cursor(SqlLikeErrorCodes.CURSOR_TOKEN_INVALID,
                    "Cursor token version is invalid or unsupported");
        }

        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) {
                throw cursor(SqlLikeErrorCodes.CURSOR_TOKEN_INVALID,
                        "Cursor token contains empty entry");
            }
            String[] parts = segment.split(",", CURSOR_ENTRY_PART_COUNT);
            if (parts.length != CURSOR_ENTRY_PART_COUNT) {
                throw cursor(SqlLikeErrorCodes.CURSOR_TOKEN_INVALID,
                        "Cursor token entry is malformed");
            }
            String field = decodeComponent(parts[0]);
            if (StringUtil.isNullOrBlank(field)) {
                throw cursor(SqlLikeErrorCodes.CURSOR_TOKEN_INVALID,
                        "Cursor token field name is invalid");
            }
            String type = parts[1];
            Object value = EncodedValue.decode(type, decodeComponent(parts[2]));
            if (values.putIfAbsent(field, value) != null) {
                throw cursor(SqlLikeErrorCodes.CURSOR_TOKEN_INVALID,
                        "Cursor token contains duplicate field '" + field + "'");
            }
        }
        return new SqlLikeCursor(values);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SqlLikeCursor that)) {
            return false;
        }
        return values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "SqlLikeCursor" + values;
    }

    public static final class Builder {
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder put(String field, Object value) {
            String normalized = normalizeField(field);
            if (value == null) {
                throw cursor(SqlLikeErrorCodes.CURSOR_VALUE_INVALID,
                        "Cursor value for field '" + normalized + "' must not be null");
            }
            values.put(normalized, value);
            return this;
        }

        public Builder putAll(Map<String, ?> values) {
            Objects.requireNonNull(values, "values must not be null");
            values.forEach(this::put);
            return this;
        }

        public SqlLikeCursor build() {
            if (values.isEmpty()) {
                throw cursor(SqlLikeErrorCodes.CURSOR_VALUE_INVALID,
                        "Cursor must include at least one field value");
            }
            return new SqlLikeCursor(values);
        }

        private static String normalizeField(String field) {
            if (StringUtil.isNullOrBlank(field)) {
                throw cursor(SqlLikeErrorCodes.CURSOR_FIELD_MISMATCH,
                        "Cursor field name must not be null/blank");
            }
            return field;
        }
    }

    private record EncodedValue(String type, String value) {
        private static EncodedValue encode(Object value) {
            if (value instanceof String stringValue) {
                return new EncodedValue("STR", stringValue);
            }
            if (value instanceof Boolean booleanValue) {
                return new EncodedValue("BOOL", Boolean.toString(booleanValue));
            }
            if (value instanceof Integer integerValue) {
                return new EncodedValue("INT", Integer.toString(integerValue));
            }
            if (value instanceof Long longValue) {
                return new EncodedValue("LONG", Long.toString(longValue));
            }
            if (value instanceof Double doubleValue) {
                return new EncodedValue("DOUBLE", Double.toString(doubleValue));
            }
            if (value instanceof Float floatValue) {
                return new EncodedValue("FLOAT", Float.toString(floatValue));
            }
            if (value instanceof Short shortValue) {
                return new EncodedValue("SHORT", Short.toString(shortValue));
            }
            if (value instanceof Byte byteValue) {
                return new EncodedValue("BYTE", Byte.toString(byteValue));
            }
            if (value instanceof BigInteger bigIntegerValue) {
                return new EncodedValue("BIGINT", bigIntegerValue.toString());
            }
            if (value instanceof BigDecimal bigDecimalValue) {
                return new EncodedValue("BIGDEC", bigDecimalValue.toPlainString());
            }
            if (value instanceof Date dateValue) {
                return new EncodedValue("DATE", Long.toString(dateValue.getTime()));
            }
            throw cursor(SqlLikeErrorCodes.CURSOR_VALUE_INVALID,
                    "Unsupported cursor token value type: " + value.getClass().getSimpleName());
        }

        private static Object decode(String type, String value) {
            try {
                return switch (type) {
                    case "STR" -> value;
                    case "BOOL" -> Boolean.valueOf(value);
                    case "INT" -> Integer.valueOf(value);
                    case "LONG" -> Long.valueOf(value);
                    case "DOUBLE" -> Double.valueOf(value);
                    case "FLOAT" -> Float.valueOf(value);
                    case "SHORT" -> Short.valueOf(value);
                    case "BYTE" -> Byte.valueOf(value);
                    case "BIGINT" -> new BigInteger(value);
                    case "BIGDEC" -> new BigDecimal(value);
                    case "DATE" -> new Date(Long.parseLong(value));
                    default -> throw cursor(SqlLikeErrorCodes.CURSOR_TOKEN_INVALID,
                            "Cursor token value type '" + type + "' is unsupported");
                };
            } catch (NumberFormatException ex) {
                throw cursor(SqlLikeErrorCodes.CURSOR_TOKEN_INVALID,
                        "Cursor token value '" + value + "' is invalid for type '" + type + "'");
            }
        }
    }

    private static IllegalArgumentException cursor(String code, String message) {
        return SqlLikeErrors.argument(code, message);
    }

    private static String encodeComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decodeComponent(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}

