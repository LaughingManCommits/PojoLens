package laughing.man.commits.sqllike.internal.expression;

import laughing.man.commits.enums.Clauses;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.NullLiteral;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.NumberLiteral;
import laughing.man.commits.sqllike.internal.expression.ExpressionNode.TextLiteral;
import laughing.man.commits.sqllike.internal.expression.ExpressionTypes.Kind;
import laughing.man.commits.sqllike.internal.expression.SqlExpressionEvaluator.ValueResolver;
import laughing.man.commits.util.ObjectUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Scalar expression functions (WP-29). Semantics follow PostgreSQL: a null argument gives a
 * null result, except {@code COALESCE}, {@code NULLIF}, and {@code CONCAT} (which skips
 * nulls). Numeric functions work on the {@code double} lane; the others override
 * {@link #value}.
 */
enum ScalarFunction implements ExpressionFunction {

    ABS(1, 1) {
        @Override
        public double number(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            return Math.abs(args.get(0).number(row, slots, resolver));
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            return numericArguments(args, fieldTypes);
        }
    },
    ROUND(1, 1) {
        @Override
        public double number(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            return Math.rint(args.get(0).number(row, slots, resolver));
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            return numericArguments(args, fieldTypes);
        }
    },
    FLOOR(1, 1) {
        @Override
        public double number(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            return Math.floor(args.get(0).number(row, slots, resolver));
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            return numericArguments(args, fieldTypes);
        }
    },
    CEIL(1, 1, "CEILING") {
        @Override
        public double number(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            return Math.ceil(args.get(0).number(row, slots, resolver));
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            return numericArguments(args, fieldTypes);
        }
    },
    LOWER(1, 1) {
        @Override
        public Object value(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            String text = textArgument(args, 0, row, slots, resolver);
            return text == null ? null : text.toLowerCase(Locale.ROOT);
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            requireText(args.get(0).type(fieldTypes));
            return String.class;
        }
    },
    UPPER(1, 1) {
        @Override
        public Object value(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            String text = textArgument(args, 0, row, slots, resolver);
            return text == null ? null : text.toUpperCase(Locale.ROOT);
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            requireText(args.get(0).type(fieldTypes));
            return String.class;
        }
    },
    /**
     * Strips leading and trailing Unicode whitespace ({@link String#strip()}); PostgreSQL
     * strips spaces only.
     */
    TRIM(1, 1) {
        @Override
        public Object value(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            String text = textArgument(args, 0, row, slots, resolver);
            return text == null ? null : text.strip();
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            requireText(args.get(0).type(fieldTypes));
            return String.class;
        }
    },
    /**
     * Length in Unicode code points.
     */
    LENGTH(1, 1) {
        @Override
        public Object value(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            String text = textArgument(args, 0, row, slots, resolver);
            return text == null ? null : text.codePointCount(0, text.length());
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            requireText(args.get(0).type(fieldTypes));
            return Integer.class;
        }
    },
    /**
     * {@code substring(text, start[, count])}: 1-based code-point positions with PostgreSQL
     * window semantics, so {@code substring('abc', 0, 2)} is {@code 'a'}.
     */
    SUBSTRING(2, 3) {
        @Override
        public Object value(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            String text = textArgument(args, 0, row, slots, resolver);
            double start = args.get(1).number(row, slots, resolver);
            double count = args.size() == 3 ? args.get(2).number(row, slots, resolver) : Double.POSITIVE_INFINITY;
            if (text == null || Double.isNaN(start) || Double.isNaN(count)) {
                return null;
            }
            long from = wholeNumber(start, "start");
            long end = Integer.MAX_VALUE;
            if (args.size() == 3) {
                long length = wholeNumber(count, "count");
                if (length < 0) {
                    throw new IllegalArgumentException("Function SUBSTRING count must not be negative");
                }
                end = from + length;
            }
            long first = Math.max(from, 1);
            long last = Math.min(end, (long) text.codePointCount(0, text.length()) + 1);
            if (last <= first) {
                return "";
            }
            int begin = text.offsetByCodePoints(0, (int) (first - 1));
            return text.substring(begin, text.offsetByCodePoints(begin, (int) (last - first)));
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            requireText(args.get(0).type(fieldTypes));
            for (int i = 1; i < args.size(); i++) {
                ExpressionNode.requireNumber(args.get(i).type(fieldTypes), "Function SUBSTRING");
            }
            return String.class;
        }
    },
    /**
     * Joins the text form of every argument, skipping nulls (PostgreSQL {@code concat()}, not
     * {@code ||}).
     */
    CONCAT(1, Integer.MAX_VALUE) {
        @Override
        public Object value(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            StringBuilder joined = new StringBuilder();
            for (ExpressionNode arg : args) {
                String text = ExpressionTypes.displayText(arg.value(row, slots, resolver));
                if (text != null) {
                    joined.append(text);
                }
            }
            return joined.toString();
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            for (ExpressionNode arg : args) {
                arg.type(fieldTypes);
            }
            return String.class;
        }
    },
    COALESCE(1, Integer.MAX_VALUE) {
        @Override
        public Object value(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            for (ExpressionNode arg : args) {
                Object value = arg.value(row, slots, resolver);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            return commonType(args, fieldTypes);
        }
    },
    /**
     * {@code nullif(a, b)}: {@code null} when {@code a = b}, otherwise {@code a}.
     */
    NULLIF(2, 2) {
        @Override
        public Object value(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
            Object first = args.get(0).value(row, slots, resolver);
            if (first == null) {
                return null;
            }
            Object second = args.get(1).value(row, slots, resolver);
            boolean equal = second != null && ObjectUtil.compareObject(first, second, Clauses.EQUAL, null);
            return equal ? null : first;
        }

        @Override
        public Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
            commonType(args, fieldTypes);
            Class<?> type = args.get(0).type(fieldTypes);
            return type == null ? Object.class : type;
        }
    };

    private static final int UNBOUNDED = Integer.MAX_VALUE;

    private static final Map<String, ScalarFunction> BY_NAME = indexByName();

    private final int minArguments;
    private final int maxArguments;
    private final String alias;

    ScalarFunction(int minArguments, int maxArguments) {
        this(minArguments, maxArguments, null);
    }

    ScalarFunction(int minArguments, int maxArguments, String alias) {
        this.minArguments = minArguments;
        this.maxArguments = maxArguments;
        this.alias = alias;
    }

    /**
     * @return the function for an upper-case name or alias, or {@code null}
     */
    static ScalarFunction lookup(String upperName) {
        return BY_NAME.get(upperName);
    }

    @Override
    public void requireArgumentCount(String calledName, int count) {
        if (count >= minArguments && count <= maxArguments) {
            return;
        }
        String expected = minArguments == maxArguments
                ? String.valueOf(minArguments)
                : maxArguments == UNBOUNDED ? "at least " + minArguments : minArguments + " to " + maxArguments;
        throw new IllegalArgumentException(
                "Function " + calledName.toUpperCase(Locale.ROOT) + " requires " + expected + " argument(s)");
    }

    /**
     * Typed value. The default serves numeric functions: {@code NaN} (a null operand) maps
     * to {@code null}.
     */
    @Override
    public Object value(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
        return SqlExpressionEvaluator.toNullable(number(args, row, slots, resolver));
    }

    /**
     * Numeric lane. The default serves value functions whose result is used in arithmetic.
     */
    @Override
    public double number(List<ExpressionNode> args, Object[] row, int[] slots, ValueResolver resolver) {
        Object value = value(args, row, slots, resolver);
        if (value == null) {
            return Double.NaN;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("Function " + name() + " returns "
                + value.getClass().getSimpleName() + ", not a number");
    }

    @Override
    public abstract Class<?> type(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes);

    Class<?> numericArguments(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
        for (ExpressionNode arg : args) {
            ExpressionNode.requireNumber(arg.type(fieldTypes), "Function " + name());
        }
        return Double.class;
    }

    String textArgument(List<ExpressionNode> args, int index, Object[] row, int[] slots, ValueResolver resolver) {
        return ExpressionTypes.text(args.get(index).value(row, slots, resolver), name());
    }

    void requireText(Class<?> type) {
        Kind kind = ExpressionTypes.kindOf(type);
        if (kind != Kind.TEXT && kind != Kind.ANY) {
            throw new IllegalArgumentException("Function " + name() + " needs text, not " + ExpressionTypes.describe(type));
        }
    }

    static long wholeNumber(double value, String argument) {
        if (!Double.isFinite(value) || Double.compare(value, Math.rint(value)) != 0) {
            throw new IllegalArgumentException("Function SUBSTRING " + argument + " must be a whole number");
        }
        return (long) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    /**
     * One result type for {@code COALESCE}/{@code NULLIF} arguments (WP-29 D6). Every known
     * argument must be the same kind. When all non-literal arguments share one class, that
     * class wins and literals convert to it on output; otherwise numbers widen to
     * {@code Double} and text to {@code String}. A field of unknown type gives
     * {@code Object}.
     */
    Class<?> commonType(List<ExpressionNode> args, Function<String, Class<?>> fieldTypes) {
        Kind kind = null;
        Class<?> kindExample = null;
        Class<?> shared = null;
        boolean mixed = false;
        boolean unknown = false;
        boolean textLiteral = false;
        for (ExpressionNode arg : args) {
            if (arg instanceof NullLiteral) {
                continue;
            }
            Class<?> type = arg.type(fieldTypes);
            Kind argKind = ExpressionTypes.kindOf(type);
            if (argKind == Kind.ANY) {
                unknown = true;
                continue;
            }
            if (kind != null && kind != argKind) {
                throw new IllegalArgumentException("Function " + name() + " arguments must share one type, not "
                        + ExpressionTypes.describe(kindExample) + " and " + ExpressionTypes.describe(type));
            }
            kind = argKind;
            kindExample = type;
            if (arg instanceof NumberLiteral) {
                continue;
            }
            if (arg instanceof TextLiteral) {
                textLiteral = true;
                continue;
            }
            if (shared == null) {
                shared = type;
            } else if (shared != type) {
                mixed = true;
            }
        }
        if (unknown || kind == null) {
            return Object.class;
        }
        if (shared != null && !mixed && !(textLiteral && shared != String.class)) {
            return shared;
        }
        return switch (kind) {
            case NUMBER -> Double.class;
            case TEXT -> String.class;
            default -> Object.class;
        };
    }

    private static Map<String, ScalarFunction> indexByName() {
        Map<String, ScalarFunction> byName = new HashMap<>();
        for (ScalarFunction function : values()) {
            byName.put(function.name(), function);
            if (function.alias != null) {
                byName.put(function.alias, function);
            }
        }
        return Map.copyOf(byName);
    }
}
