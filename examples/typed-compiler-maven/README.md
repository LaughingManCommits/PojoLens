# Typed Compiler Maven Example

This standalone Maven example shows compiler-generated typed field constants.

The model is annotated with `@GeneratePojoLensTypedFields`, and the Maven
compiler plugin explicitly enables `PojoLensTypedFieldsProcessor`. During
`compile`, javac writes `EmployeeTypedFields` as ordinary generated Java source
under `target/generated-sources/annotations`.

Run from the repository root after the runtime artifact has been installed to
the local Maven repository:

```bash
mvn -B -ntp -pl pojo-lens install -DskipTests
mvn -B -ntp -f examples/typed-compiler-maven/pom.xml test
```

This example is not a published artifact.
