package laughing.man.commits.examples.typedkotlin

import laughing.man.commits.dsl.TypedField

object TypedKotlinExample {
    fun departmentField(): TypedField<Employee, String> = EmployeeTypedFields.DEPARTMENT

    fun salaryField(): TypedField<Employee, Int> = EmployeeTypedFields.SALARY

    fun generatedFieldCount(): Int = EmployeeTypedFields.ALL.size
}
