package laughing.man.commits.examples.typedgradle;

import laughing.man.commits.annotations.Exclude;
import laughing.man.commits.annotations.GeneratePojoLensTypedFields;

@GeneratePojoLensTypedFields
public class Employee {

    public boolean active;
    public String department;
    public int salary;
    public Status status;
    @Exclude
    public String internalCode;

    public enum Status {
        ACTIVE,
        INACTIVE
    }
}
