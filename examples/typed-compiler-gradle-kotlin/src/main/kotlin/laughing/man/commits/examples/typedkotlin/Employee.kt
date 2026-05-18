package laughing.man.commits.examples.typedkotlin

import laughing.man.commits.annotations.Exclude
import laughing.man.commits.annotations.GeneratePojoLensTypedFields

@GeneratePojoLensTypedFields
class Employee {
    @JvmField
    var active: Boolean = false

    @JvmField
    var department: String = ""

    @JvmField
    var salary: Int = 0

    @JvmField
    var status: Status = Status.ACTIVE

    @field:Exclude
    @JvmField
    var internalCode: String = ""

    enum class Status {
        ACTIVE,
        INACTIVE
    }
}
