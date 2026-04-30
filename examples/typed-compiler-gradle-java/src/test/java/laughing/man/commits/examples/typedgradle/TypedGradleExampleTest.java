package laughing.man.commits.examples.typedgradle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypedGradleExampleTest {

    @Test
    void generatedFieldsAreUsableFromGradleBuild() {
        assertEquals("department", TypedGradleExample.departmentField().fieldName());
        assertEquals(Integer.class, TypedGradleExample.salaryField().valueType());
        assertEquals(4, TypedGradleExample.generatedFieldCount());
    }
}
