# 🚀 Maven Setup

The Maven plugin generates the Process API from your BPMN models. It is published on [Maven Central](https://central.sonatype.com/artifact/io.miragon/bpmn-to-code-maven).

**Requirements:** Maven runs on JDK 21 or newer. The plugin and `bpmn-to-code-runtime` are compiled for Java 21.

## 1. Add the plugin and the runtime {#runtime-dependency}

Generated Kotlin and Java code imports types from `bpmn-to-code-runtime`. The Maven plugin does not add that dependency, so declare it next to the plugin, in the same version:

<!-- x-release-please-start-version -->
```xml
<dependencies>
    <dependency>
        <groupId>io.miragon</groupId>
        <artifactId>bpmn-to-code-runtime</artifactId>
        <version>6.2.0</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>io.miragon</groupId>
            <artifactId>bpmn-to-code-maven</artifactId>
            <version>6.2.0</version>
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
                <filePattern>src/main/resources/*.bpmn</filePattern>
                <outputFolderPath>${project.basedir}/src/main/kotlin</outputFolderPath>
                <packagePath>com.example.process</packagePath>
                <outputLanguage>KOTLIN</outputLanguage>
                <processEngine>ZEEBE</processEngine>
            </configuration>
        </plugin>
    </plugins>
</build>
```
<!-- x-release-please-end -->

The goals are bound to **no lifecycle phase by default**. An `<execution>` without `<phase>` never runs during `mvn compile` or `mvn package`; bind it as above, or run the goal directly.

Relative paths resolve against the directory Maven is started in, not against the module, so prefix `baseDir` and `outputFolderPath` with `${project.basedir}`. C# output needs no runtime dependency. See [Configuration](/guide/configuration) for every parameter and its default.

## 2. Generate the API

With the execution bound to `generate-sources`, every build from that phase on generates:

```bash
mvn generate-sources
```

To run only the goal, call it by the plugin's prefix. It uses the plugin-level `<configuration>`:

```bash
mvn bpmn-to-code:generate-bpmn-api
```

The [generated files](/guide/generated-api) appear below `outputFolderPath`, in the directory of `packagePath`. Generation first runs the [built-in validation rules](/validate/) and fails on any error.

## The three goals

| Goal | Purpose |
|------|---------|
| `generate-bpmn-api` | Generates the [Process API](/guide/generated-api) |
| `generate-bpmn-json` | Generates the [JSON export](/surface/json) |
| `validate-bpmn` | [Validates](/validate/) the models without generating (experimental) |

The prefix `bpmn-to-code` resolves in a project whose POM declares the plugin. Anywhere else, use the full coordinates. No goal needs a POM, and every parameter is also a user property:

<!-- x-release-please-start-version -->
```bash
mvn io.miragon:bpmn-to-code-maven:6.2.0:validate-bpmn \
    -DbaseDir=. -DfilePattern="src/main/resources/*.bpmn" -DprocessEngine=ZEEBE
```
<!-- x-release-please-end -->

## Several executions

One execution reads one set of files for one engine, one language and one package. Add an execution per group, each with its own `<id>` and `<configuration>`:

```xml
<executions>
    <execution>
        <id>generate-c7</id>
        <phase>generate-sources</phase>
        <goals><goal>generate-bpmn-api</goal></goals>
        <configuration>
            <baseDir>${project.basedir}</baseDir>
            <filePattern>src/main/resources/c7/*.bpmn</filePattern>
            <outputFolderPath>${project.basedir}/src/main/kotlin</outputFolderPath>
            <packagePath>com.example.c7</packagePath>
            <outputLanguage>KOTLIN</outputLanguage>
            <processEngine>CAMUNDA_7</processEngine>
        </configuration>
    </execution>
    <execution>
        <id>generate-zeebe</id>
        <phase>generate-sources</phase>
        <goals><goal>generate-bpmn-api</goal></goals>
        <configuration>
            <baseDir>${project.basedir}</baseDir>
            <filePattern>src/main/resources/c8/*.bpmn</filePattern>
            <outputFolderPath>${project.basedir}/src/main/kotlin</outputFolderPath>
            <packagePath>com.example.c8</packagePath>
            <outputLanguage>KOTLIN</outputLanguage>
            <processEngine>ZEEBE</processEngine>
        </configuration>
    </execution>
</executions>
```

Give each execution its own package: executions generating into the same package [delete each other's files](/guide/generated-api#shared-definitions). To run a single one from the command line, name it: `mvn bpmn-to-code:generate-bpmn-api@generate-zeebe`.

## Filtering files

A goal reads every file matching `filePattern`; there is no exclude. To leave files out, copy the ones you want with the [maven-resources-plugin](https://maven.apache.org/plugins/maven-resources-plugin/) and generate from that directory. Declare it before bpmn-to-code and bind both to `generate-sources`, since Maven runs the plugins of one phase in POM order:

```xml
<plugin>
    <artifactId>maven-resources-plugin</artifactId>
    <executions>
        <execution>
            <id>collect-bpmn</id>
            <phase>generate-sources</phase>
            <goals><goal>copy-resources</goal></goals>
            <configuration>
                <outputDirectory>${project.build.directory}/bpmn-staging</outputDirectory>
                <resources>
                    <resource>
                        <directory>src/main/resources</directory>
                        <includes><include>**/*.bpmn</include></includes>
                        <excludes><exclude>**/draft-*.bpmn</exclude></excludes>
                    </resource>
                </resources>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Then point bpmn-to-code at it with `<baseDir>${project.build.directory}/bpmn-staging</baseDir>` and `<filePattern>**/*.bpmn</filePattern>`.

## Next steps

- [Generated API](/guide/generated-api): what the files contain and how to use them.
- [Modeling](/guide/modeling): how ids and variable declarations in the model shape the API.
- [AI Skills](/skills/): `setup-bpmn-to-code-maven` configures the plugin for you, `migrate-to-bpmn-to-code-apis` replaces hardcoded strings with the generated API.
