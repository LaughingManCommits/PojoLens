# Typed Compiler Gradle Java Example

This standalone Gradle example shows compiler-generated typed field constants
for a Java model.

The model is annotated with `@GeneratePojoLensTypedFields`, and the Gradle
`annotationProcessor` dependency points at the PojoLens artifact. The processor
is service-loadable and ships Gradle incremental annotation-processor metadata,
so Gradle can discover it on the annotation processor path and treat it as an
isolating processor.

Run from the repository root after the runtime artifact has been installed to
the local Maven repository:

```bash
mvn -B -ntp -pl pojo-lens install -DskipTests
gradle -p examples/typed-compiler-gradle-java test
```

This example is not a published artifact.
