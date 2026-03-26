package laughing.man.commits.builder;

import java.io.Serializable;
import java.util.function.Function;

@FunctionalInterface
public interface FieldSelector<T, R> extends Function<T, R>, Serializable {
}

