package laughing.man.commits.examples.typedkotlin

import kotlin.test.Test
import kotlin.test.assertEquals

class TypedKotlinExampleTest {
    @Test
    fun generatedFieldsAreUsableFromKotlin() {
        assertEquals("department", TypedKotlinExample.departmentField().fieldName())
        assertEquals(Int::class.javaObjectType, TypedKotlinExample.salaryField().valueType())
        assertEquals(4, TypedKotlinExample.generatedFieldCount())
    }
}
