package laughing.man.commits.examples.typedgradle;

import laughing.man.commits.dsl.TypedField;

public final class TypedGradleExample {

    private TypedGradleExample() {
    }

    public static TypedField<Employee, String> departmentField() {
        return EmployeeTypedFields.DEPARTMENT;
    }

    public static TypedField<Employee, Integer> salaryField() {
        return EmployeeTypedFields.SALARY;
    }

    public static int generatedFieldCount() {
        return EmployeeTypedFields.ALL.size();
    }
}
