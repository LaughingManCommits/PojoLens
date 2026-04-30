# Typed Authoring Kotlin Gradle Example

This standalone Gradle example shows Kotlin/JVM code using compiler-generated
typed field constants through kapt.

The current processor is field-model based so that compiler output matches the
Java reflection generator. Kotlin model fields that should become PojoLens
query fields use `@JvmField var`; plain Kotlin properties and immutable `val`
properties are intentionally left for a future Kotlin-property or KSP-specific
design.

Run from the repository root after the runtime artifact has been installed to
the local Maven repository:

```bash
mvn -B -ntp -pl pojo-lens install -DskipTests
gradle -p examples/typed-authoring-kotlin-gradle test
```

This example is not a published artifact.
