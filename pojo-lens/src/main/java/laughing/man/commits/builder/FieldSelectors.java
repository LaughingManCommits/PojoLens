package laughing.man.commits.builder;

import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

public final class FieldSelectors {

    private static final int GET_PREFIX_LENGTH = 3;
    private static final int IS_PREFIX_LENGTH = 2;

    private FieldSelectors() {
    }

    public static <T, R> String resolve(FieldSelector<T, R> selector) {
        try {
            Method writeReplace = selector.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(selector);
            String method = lambda.getImplMethodName();
            if (method.startsWith("get") && method.length() > GET_PREFIX_LENGTH) {
                return Introspector.decapitalize(method.substring(GET_PREFIX_LENGTH));
            }
            if (method.startsWith("is") && method.length() > IS_PREFIX_LENGTH) {
                return Introspector.decapitalize(method.substring(IS_PREFIX_LENGTH));
            }
            return method;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to resolve field from selector", e);
        }
    }
}

