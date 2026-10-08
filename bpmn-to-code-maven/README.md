# bpmn-to-code-maven

Maven plugin of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code), published to [Maven Central](https://central.sonatype.com/artifact/io.miragon/bpmn-to-code-maven).

<!-- x-release-please-start-version -->
```xml
<plugin>
    <groupId>io.miragon</groupId>
    <artifactId>bpmn-to-code-maven</artifactId>
    <version>6.2.0</version>
</plugin>
```
<!-- x-release-please-end -->

| Goal | Does |
|---|---|
| `generate-bpmn-api` | Generates the Process API from BPMN files |
| `generate-bpmn-json` | Generates the process JSON |
| `validate-bpmn` | Validates BPMN files without generating code (experimental) |

No goal is bound to a lifecycle phase by default; bind it in an `<execution>` or call it directly, for example `mvn io.miragon:bpmn-to-code-maven:generate-bpmn-api`. Generated Kotlin and Java code needs `io.miragon:bpmn-to-code-runtime` in the same version as a dependency; unlike the Gradle plugin, the Maven plugin does not add it.

Setup and all parameters: [Maven guide](https://miragon.github.io/bpmn-to-code/getting-started/maven) and [Configuration](https://miragon.github.io/bpmn-to-code/guide/configuration).
