# bpmn-to-code Maven plugin

[![Maven Central](https://img.shields.io/maven-central/v/io.miragon/bpmn-to-code-maven?label=Maven%20Central&color=335DE5)](https://central.sonatype.com/artifact/io.miragon/bpmn-to-code-maven)

**Generate the typed Process API from your BPMN models in your Maven build.**

The Maven plugin of [bpmn-to-code](https://github.com/Miragon/bpmn-to-code) turns element ids, job types and message names into constants for your workers and tests. It needs JDK 21 or newer.

## Install

Generated Kotlin and Java code needs `io.miragon:bpmn-to-code-runtime` in the same version; unlike the Gradle plugin, the Maven plugin does not add it, and it binds to no phase by itself:

<!-- x-release-please-start-version -->
```xml
<dependencies>
    <dependency>
        <groupId>io.miragon</groupId>
        <artifactId>bpmn-to-code-runtime</artifactId>
        <version>7.1.0</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>io.miragon</groupId>
            <artifactId>bpmn-to-code-maven</artifactId>
            <version>7.1.0</version>
            <executions>
                <execution>
                    <phase>generate-sources</phase>
                    <goals><goal>generate-bpmn-api</goal></goals>
                </execution>
            </executions>
            <configuration>
                <baseDir>${project.basedir}</baseDir>
                <filePattern>src/main/resources/*.bpmn</filePattern>
                <outputFolderPath>${project.basedir}/src/main/java</outputFolderPath>
                <packagePath>com.example.process</packagePath>
                <outputLanguage>JAVA</outputLanguage>
                <processEngine>ZEEBE</processEngine>
            </configuration>
        </plugin>
    </plugins>
</build>
```
<!-- x-release-please-end -->

Then run `mvn generate-sources`.

## Goals

| Goal                | Does                                                        |
| ------------------- | ----------------------------------------------------------- |
| `generate-bpmn-api` | Generates the Process API from BPMN files                   |
| `validate-bpmn`     | Validates BPMN files without generating code (experimental) |

Call a goal directly with `mvn io.miragon:bpmn-to-code-maven:<goal>`; it reads the plugin-level `<configuration>`.

## Help and feedback

- **Setup:** [Maven guide](https://miragon.github.io/bpmn-to-code/getting-started/maven)
- **All parameters:** [Configuration](https://miragon.github.io/bpmn-to-code/guide/configuration)
- **Bugs and ideas:** [open an issue](https://github.com/Miragon/bpmn-to-code/issues)

## License

[MIT](../LICENSE)
