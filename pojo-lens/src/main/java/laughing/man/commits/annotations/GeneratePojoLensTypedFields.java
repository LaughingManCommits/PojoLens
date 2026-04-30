package laughing.man.commits.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requests compile-time generation of PojoLens {@code TypedField<T,V>} constants for a model type.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface GeneratePojoLensTypedFields {

    /**
     * Generated package. Defaults to the annotated model package.
     *
     * @return generated package name, or blank for the model package
     */
    String packageName() default "";

    /**
     * Generated simple class name. Defaults to {@code <ModelSimpleName>TypedFields}.
     *
     * @return generated simple class name, or blank for the default name
     */
    String simpleName() default "";
}
