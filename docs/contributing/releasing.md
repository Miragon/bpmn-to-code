# Releasing

Releases are cut by [release-please](https://github.com/googleapis/release-please) and published by GitHub Actions. Nobody edits a version, creates a tag or pushes an artifact by hand.

## What gets published

Everything shares one version, `projectVersion` in `gradle.properties`.

| Artifact | Destination | Workflow |
|---|---|---|
| `io.miragon:bpmn-to-code-runtime`, `bpmn-to-code-maven`, `bpmn-to-code-testing` | Maven Central | `publish-to-maven.yml` |
| Gradle plugin `io.miragon.bpmn-to-code-gradle` | Gradle Plugin Portal | `publish-to-gradle.yml` |
| `miragon/bpmn-to-code-web:<version>` and `:latest` | Docker Hub | `publish-to-docker.yml` |
| Deployment of the hosted web app | Pull request in `Miragon/ops` | `update-ops-deployment.yml` |
| This documentation site | GitHub Pages | `deploy-docs.yml` |

## Release flow

Everything runs in one workflow, `release-please.yml`, on every push to `main`:

1. release-please opens or updates the **Release PR**. It derives the next version from the Conventional Commit titles on `main`, updates `CHANGELOG.md`, and rewrites the version in `gradle.properties` and in every file listed under `extra-files` in `release-please-config.json`.
2. Merging the Release PR creates the tag (`v<version>`) and the GitHub release.
3. The run then waits at the `release` environment until a maintainer approves it. One approval unblocks all publish jobs.
4. Maven Central is published first, then the Gradle Plugin Portal. The order matters: the Gradle plugin adds `bpmn-to-code-runtime` in its own version to consuming projects, so the runtime has to be resolvable before the plugin is.
5. In parallel, the Docker image is built for `linux/amd64` and pushed as `<version>` and `latest`, and `bpmn-to-code-web/README.md` is uploaded as the Docker Hub description. Afterwards a pull request in `Miragon/ops` bumps the deployment to the new version; it is merged by hand.
6. The documentation is built and deployed.

Each publish workflow runs `./gradlew build` before it publishes.

A file that carries the current version as a literal must be listed in `extra-files` and wrap the version in `x-release-please-start-version` / `x-release-please-end` markers. Otherwise it keeps the old number after the next release.

### Dry run

```bash
gh workflow run release-please.yml -f dry_run=true
```

This skips the Release PR and the GitHub release and runs every publish job in build-and-verify mode: Maven artifacts go to the local repository, the Gradle plugin is validated only, the image is built but not pushed, the ops change is shown as a diff, the docs are built but not deployed. The approval gate still applies. The publish workflows accept the same `dry_run` input when dispatched on their own.

### Documentation

The site is deployed only as part of a release, so a docs change on `main` is not online until the next release. To publish it earlier:

```bash
gh workflow run deploy-docs.yml
```

## Snapshots

A snapshot publishes the state of any branch for trying out unreleased changes. It is dispatched by hand and is not gated by the `release` environment:

```bash
gh workflow run publish-snapshot.yml --ref <branch> -f version=6.3.0-SNAPSHOT
```

The version must end in `-SNAPSHOT`; use the upcoming version rather than the one in `gradle.properties`, since Maven orders the snapshot of an already released version below that release. Snapshots are mutable: publishing the same version again replaces it. The workflow

1. builds and tests the branch,
2. uploads `bpmn-to-code-runtime`, `bpmn-to-code-maven` and `bpmn-to-code-testing` to the [Central Snapshots repository](https://central.sonatype.com/repository/maven-snapshots/),
3. uploads the Gradle plugin and its plugin marker to the same repository, because the Plugin Portal does not host snapshots (release versions never take this path),
4. builds the Docker image and pushes it as `miragon/bpmn-to-code-web:6.3.0-SNAPSHOT` only, without touching `latest` or the Docker Hub description,
5. reads the digest of the pushed image and opens a pull request in `Miragon/ops` that pins the separate snapshot deployment to that digest. This pull request is merged automatically, so the snapshot instance of the web app rolls out without further action.

### Using a snapshot

Gradle resolves the plugin through its marker artifact:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
        gradlePluginPortal()
    }
}

// build.gradle.kts
plugins {
    id("io.miragon.bpmn-to-code-gradle") version "6.3.0-SNAPSHOT"
}

repositories {
    maven { url = uri("https://central.sonatype.com/repository/maven-snapshots/") }
    mavenCentral()
}
```

The `repositories` entry is needed for the runtime dependency the plugin adds. Maven needs the repository for the plugin and for the runtime dependency:

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

Then use `6.3.0-SNAPSHOT` as the version of the plugin and of `bpmn-to-code-runtime`, configured as in the [Gradle](/getting-started/gradle) and [Maven](/getting-started/maven) guides.

## Building the image locally

```bash
./gradlew :bpmn-to-code-web:dockerBuild   # fat jar, then the image, tagged <version> and latest
./gradlew :bpmn-to-code-web:dockerRun     # starts it detached on http://localhost:9099
```

The tasks call the `docker` executable found on the `PATH`. Pushing is left to the workflows.
