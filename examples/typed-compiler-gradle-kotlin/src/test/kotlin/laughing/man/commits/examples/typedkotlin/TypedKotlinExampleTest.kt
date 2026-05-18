package laughing.man.commits.examples.typedkotlin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TypedKotlinExampleTest {
    @Test
    fun generatedFieldsAreUsableFromKotlin() {
        assertEquals("department", TypedKotlinExample.departmentField().fieldName())
        assertEquals(Int::class.javaObjectType, TypedKotlinExample.salaryField().valueType())
        assertEquals(4, TypedKotlinExample.generatedFieldCount())
    }
}
