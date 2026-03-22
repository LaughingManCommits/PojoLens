package laughing.man.commits.builder;

import java.beans.Introspector;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

public final class FieldSelectors {

    private FieldSelectors() {
    }

    public static <T, R> String resolve(FieldSelector<T, R> selector) {
        try {
            Method writeReplace = selector.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(selector);
            String method = lambda.getImplMethodName();
            if (method.startsWith("get") && method.length() > 3) {
                return Introspector.decapitalize(method.substring(3));
            }
            if (method.startsWith("is") && method.length() > 2) {
                return Introspector.decapitalize(method.substring(2));
            }
            return method;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to resolve field from selector", e);
        }
    }
}

