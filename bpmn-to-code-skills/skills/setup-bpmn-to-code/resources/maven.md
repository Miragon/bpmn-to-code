# Maven

The plugin `io.miragon:bpmn-to-code-maven` comes from Maven Central and needs JDK 21+. Its goals have **no default phase**: an `<execution>` without `<phase>` never runs.

## Plugin, runtime and generation

The Maven plugin does not add the runtime. Declare it next to the plugin, in the same version:

```xml
<dependencies>
    <dependency>
        <groupId>io.miragon</groupId>
        <artifactId>bpmn-to-code-runtime</artifactId>
        <version>${bpmn-to-code.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>io.miragon</groupId>
            <artifactId>bpmn-to-code-maven</artifactId>
            <version>${bpmn-to-code.version}</version>
            <executions>
                <execution>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>generate-bpmn-api</goal>
                    </goals>
                </execution>
            </executions>
            <configuration>
                <baseDir>${project.basedir}</baseDir>
                <filePattern>src/main/resources/bpmn/*.bpmn</filePattern>
                <outputFolderPath>${project.basedir}/src/main/java</outputFolderPath>
                <packagePath>io.miragon.example.adapter.process</packagePath>
                <outputLanguage>JAVA</outputLanguage>
                <processEngine>ZEEBE</processEngine>
            </configuration>
        </plugin>
    </plugins>
</build>
```

- Add `<bpmn-to-code.version><version></bpmn-to-code.version>` to `<properties>`.
- **Prefix both paths with `${project.basedir}`.** Relative paths resolve against the directory Maven starts in, not against the module.
- These are the only parameters of `generate-bpmn-api`: `baseDir`, `filePattern`, `outputFolderPath`, `packagePath`, `outputLanguage`, `processEngine`.

**In the build directory** instead of the source folder: set `<outputFolderPath>${project.build.directory}/generated-sources/bpmn</outputFolderPath>`. Then register that folder with `build-helper-maven-plugin` (`add-source`, phase `generate-sources`) so the compiler sees it.

## Several engines or packages

Use one `<execution>` per group, each with its own `<id>`, `<phase>generate-sources</phase>` and `<configuration>`. Give each its own package.

## Model validation test (optional)

```xml
<dependency>
    <groupId>io.miragon</groupId>
    <artifactId>bpmn-to-code-testing</artifactId>
    <version>${bpmn-to-code.version}</version>
    <scope>test</scope>
</dependency>
```

```java
// src/test/java/<package>/adapter/process/model/<Process>ModelValidationTest.java
class BikeLeasingModelValidationTest {

    @Test
    @DisplayName("the bpmn models satisfy all rules")
    void theBpmnModelsSatisfyAllRules() {
        BpmnValidator
                .fromClasspath("bpmn/")
                .engine(ProcessEngine.ZEEBE)
                .withRules(BpmnRules.all())
                .validate()
                .assertNoViolations();
    }
}
```

Imports: `io.miragon.bpmn.testing.BpmnValidator`, `io.miragon.bpmn.testing.BpmnRules`, `io.miragon.bpmn.domain.shared.ProcessEngine`.

## Verify

```bash
mvn generate-sources
mvn test -Dtest='*ModelValidationTest'
```

`mvn bpmn-to-code:generate-bpmn-api` runs only the goal, with the plugin-level `<configuration>`.
