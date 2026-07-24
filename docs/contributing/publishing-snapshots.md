# Publishing & Consuming Snapshots

On demand, the current state of `main` can be published as a `-SNAPSHOT` build to the
[Maven Central Snapshots](https://central.sonatype.com/repository/maven-snapshots/) repository. This
lets you try unreleased changes in a real project without waiting for a release — no local
`publishToMavenLocal` needed.

Snapshots are **mutable**: a given `-SNAPSHOT` version is overwritten on each publish, so it always
reflects the state of `main` at the time it was last published.

## For maintainers

Publishing is handled by the [`publish-snapshot.yml`](https://github.com/Miragon/bpmn-to-code/blob/main/.github/workflows/publish-snapshot.yml)
workflow:

- **Runs on demand only** (`workflow_dispatch`) — never automatically on push:
  ```bash
  gh workflow run publish-snapshot.yml -f version=5.1.0-SNAPSHOT
  ```
- **Version** is a required input and must end with `-SNAPSHOT` (the run fails otherwise). Use the
  current `projectVersion` from `gradle.properties` with a `-SNAPSHOT` suffix. It is not gated behind
  the release approval environment.
- **Modules published:** `bpmn-to-code-runtime`, `bpmn-to-code-maven`, `bpmn-to-code-testing` (via
  the Central Portal, using `publishToMavenCentral` — upload only, no close/release step), plus the
  Gradle plugin `bpmn-to-code-gradle`.

Because the **Gradle Plugin Portal cannot host SNAPSHOTs**, the Gradle plugin (and its plugin-marker
artifact) is published to Central Snapshots instead. This path is guarded by an `isSnapshot` check in
`bpmn-to-code-gradle/build.gradle.kts`, so release versions never land there — the Portal stays the
release channel.

> **Version-ordering note:** right after a release, `projectVersion` equals the last *released*
> version (e.g. `5.1.0`), so the snapshot is `5.1.0-SNAPSHOT`, which Maven orders *below* `5.1.0`.
> That is expected for a rolling "latest `main`" channel.

## Consuming a snapshot — Gradle

Add the snapshot repository to `pluginManagement` in your `settings.gradle.kts`, alongside the
Plugin Portal, then apply the plugin at a `-SNAPSHOT` version. Gradle resolves it via the
plugin-marker artifact published to Central Snapshots — the Portal itself never hosts snapshots.

::: code-group

```kotlin [settings.gradle.kts]
pluginManagement {
    repositories {
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
        gradlePluginPortal()
    }
}
```

```groovy [settings.gradle]
pluginManagement {
    repositories {
        maven { url = 'https://central.sonatype.com/repository/maven-snapshots/' }
        gradlePluginPortal()
    }
}
```

:::

::: code-group

```kotlin [build.gradle.kts]
plugins {
    id("io.miragon.bpmn-to-code-gradle") version "5.1.0-SNAPSHOT"
}
```

```groovy [build.gradle]
plugins {
    id 'io.miragon.bpmn-to-code-gradle' version '5.1.0-SNAPSHOT'
}
```

:::

Use the current `projectVersion` from `main` with a `-SNAPSHOT` suffix. To always pull the freshest
build, tell Gradle not to cache changing modules:

```kotlin
configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}
```

## Consuming a snapshot — Maven

Declare the snapshot repository for both plugins and dependencies, then reference the plugin (and the
`bpmn-to-code-runtime` dependency) at a `-SNAPSHOT` version.

```xml
<pluginRepositories>
    <pluginRepository>
        <id>central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
    </pluginRepository>
</pluginRepositories>

<repositories>
    <repository>
        <id>central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases><enabled>false</enabled></releases>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>
```

```xml
<plugin>
    <groupId>io.miragon</groupId>
    <artifactId>bpmn-to-code-maven</artifactId>
    <version>5.1.0-SNAPSHOT</version>
    <!-- executions / configuration as in the Maven Setup guide -->
</plugin>
```

See the [Maven Setup](/getting-started/maven) and [Gradle Setup](/getting-started/gradle) guides for
the full plugin configuration.
