package laughing.man.commits.examples.typedcompiler;

import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

@GeneratePojoLensTypedFields
public class Employee {
    public String name;
    public String department;
    public int salary;
    public boolean active;

    public Employee() {
    }

    public Employee(String name, String department, int salary, boolean active) {
        this.name = name;
        this.department = department;
        this.salary = salary;
        this.active = active;
    }
}
