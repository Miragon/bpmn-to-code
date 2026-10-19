plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.pitest)
    application
    jacoco
}

group = "io.miragon"
version = property("projectVersion").toString()

repositories {
    mavenCentral()
}

dependencies {
    // Core dependency - reuse existing logic
    implementation(project(":bpmn-to-code-core"))

    // Ktor dependencies
    implementation(libs.bundles.ktor)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.logbackClassic)
    implementation(libs.kotlinLogging)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.ktorServerTestHost)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

ktor {
    fatJar {
        archiveFileName.set("bpmn-to-code-web-${version}-fat.jar")
    }


}

// Enable zip64 for shadow JAR (required for archives with >65535 entries)
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    isZip64 = true
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("shared"))
    }
}

// The sources of each runtime library, shown next to the generated code: one directory per output language
val librarySourcesDir = layout.buildDirectory.dir("generated/resources/library-sources/library-sources")

val copyLibrarySources by tasks.registering(Sync::class) {
    into(librarySourcesDir)
    preserve { include("*/manifest.txt") }
    from(project(":bpmn-to-code-runtime").layout.projectDirectory.dir("src/main/kotlin/io/miragon/bpmn/runtime")) {
        include("*.kt")
        into("kotlin")
    }
    from(rootProject.layout.projectDirectory.dir("packages/nuget/src/Miragon.BpmnToCode.Runtime")) {
        include("**/*.cs")
        exclude("bin/**", "obj/**")
        into("csharp")
    }
}

// Bundle one engine-specific bike-leasing example per engine for the demo
val copyExampleModels by tasks.registering(Copy::class) {
    listOf("zeebe", "c7", "operaton").forEach { engine ->
        from(rootProject.layout.projectDirectory.dir("shared/bpmn/$engine")) {
            include("bike-leasing.bpmn")
            rename { "$engine-bike-leasing.bpmn" }
        }
    }
    into(layout.buildDirectory.dir("generated/resources/examples/examples"))
}

val generateLibrarySourcesManifest by tasks.registering {
    dependsOn(copyLibrarySources)
    val languages = listOf("kotlin", "csharp")
    val sourcesDir = librarySourcesDir
    outputs.files(languages.map { language -> sourcesDir.map { it.file("$language/manifest.txt") } })
    doLast {
        languages.forEach { language ->
            val dir = sourcesDir.get().dir(language).asFile
            val names = dir.walkTopDown().filter { it.isFile && it.name != "manifest.txt" }.map { it.relativeTo(dir).invariantSeparatorsPath }.sorted()
            dir.resolve("manifest.txt").writeText(names.joinToString("\n") + "\n")
        }
    }
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/resources/library-sources"))
    resources.srcDir(layout.buildDirectory.dir("generated/resources/examples"))
}

tasks.named("processResources") {
    dependsOn(generateLibrarySourcesManifest, copyExampleModels)
}

application {
    mainClass.set("io.miragon.bpmn.web.ApplicationKt")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

private val coverageExclusions = listOf(
    "**/routes/**",           // thin Ktor adapter layer — integration-tested separately
    "**/web/Application*",    // application entry point / wiring
    "**/web/config/**",       // pure configuration data classes
    "**/model/ConfigResponse*",
)

tasks.jacocoTestReport {
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } })
    )
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } })
    )
}

pitest {
    excludedClasses.addAll(
        "io.miragon.bpmn.web.routes.*",
        "io.miragon.bpmn.web.Application*",
        "io.miragon.bpmn.web.config.*",
        "io.miragon.bpmn.web.model.ConfigResponse*",
    )
    mutationThreshold.set(90)
}

tasks.named<ProcessResources>("processResources") {
    val projectVersion = project.version.toString()
    inputs.property("projectVersion", projectVersion)
    filesMatching("version.properties") {
        expand("projectVersion" to projectVersion)
    }
}

// Docker tasks
val dockerImageName = "miragon/bpmn-to-code-web"
val dockerImageTag = project.version.toString()

val dockerExecutable: Provider<String> =
    providers.exec { commandLine("which", "docker") }
        .standardOutput.asText.map { it.trim().ifEmpty { "docker" } }.orElse("docker")

tasks.register<Exec>("dockerBuild") {
    group = "docker"
    description = "Build Docker image for bpmn-to-code-web"

    dependsOn("buildFatJar")
    workingDir = rootProject.projectDir

    val imageName = dockerImageName
    val imageTag = dockerImageTag
    val dockerExec = dockerExecutable

    doFirst {
        val docker = dockerExec.get()

        // Remove old tags before building so they don't become <none>:<none> dangling images.
        listOf("$imageName:$imageTag", "$imageName:latest").forEach { tag ->
            ProcessBuilder(docker, "rmi", tag).inheritIO().start().waitFor()
        }

        println("Building Docker image: $imageName:$imageTag")
        println("Using docker: $docker")
        commandLine(
            docker, "build",
            "--platform", "linux/amd64",
            "-f", "bpmn-to-code-web/Dockerfile",
            "-t", "$imageName:$imageTag",
            "-t", "$imageName:latest",
            "."
        )
    }
}

tasks.register<Exec>("dockerPush") {
    group = "docker"
    description = "Push Docker image to Docker Hub"

    dependsOn("dockerBuild")
    workingDir = rootProject.projectDir

    val imageName = dockerImageName
    val imageTag = dockerImageTag
    val dockerExec = dockerExecutable

    doFirst {
        commandLine(dockerExec.get(), "push", "$imageName:$imageTag")
    }

    doLast {
        println("Pushed image: $imageName:$imageTag")
    }
}

tasks.register<Exec>("dockerRun") {
    group = "docker"
    description = "Run Docker container locally on port 8080"

    dependsOn("dockerBuild")
    workingDir = rootProject.projectDir

    val imageName = dockerImageName
    val imageTag = dockerImageTag
    val dockerExec = dockerExecutable

    doFirst {
        commandLine(
            dockerExec.get(), "run",
            "-p", "9099:8080",
            "--rm",
            "-d",
            "$imageName:$imageTag"
        )
    }
}