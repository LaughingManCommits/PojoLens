package laughing.man.commits.examples.typedcompiler;

import laughing.man.commits.dsl.TypedQuery;

import java.util.List;

public final class TypedCompilerExample {

    private TypedCompilerExample() {
    }

    public static List<Employee> activeEngineering(List<Employee> employees) {
        return TypedQuery.from(Employee.class)
                .where(EmployeeTypedFields.ACTIVE.eq(true)
                        .and(EmployeeTypedFields.DEPARTMENT.eq("Engineering")))
                .orderByDesc(EmployeeTypedFields.SALARY)
                .filter(employees);
    }
}
