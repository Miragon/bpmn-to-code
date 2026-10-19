plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    jacoco
}

group = "io.miragon"
version = property("projectVersion").toString()

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":bpmn-to-code-core"))
    implementation(libs.cliktCore)
    runtimeOnly(libs.slf4jSimple)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

application {
    applicationName = "bpmn-to-code"
    mainClass.set("io.miragon.bpmn.cli.MainKt")
}

tasks.jar {
    manifest.attributes("Implementation-Version" to project.version)
}

val cliPlatform = platformOf(System.getProperty("os.name"), System.getProperty("os.arch"))

val jlinkExecutable = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }.map { launcher ->
    val fileName = if (cliPlatform.startsWith("win32")) "jlink.exe" else "jlink"
    launcher.metadata.installationPath.file("bin/$fileName").asFile.path
}

val runtimeImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Links the trimmed Java runtime the CLI archive ships with"
    val outputDir = layout.buildDirectory.dir("runtime-image")
    val jlink = jlinkExecutable
    outputs.dir(outputDir)
    doFirst {
        outputDir.get().asFile.deleteRecursively()
        commandLine(
            jlink.get(),
            "--add-modules", "java.base,java.xml,java.compiler,java.logging,java.desktop",
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--compress=zip-6",
            "--output", outputDir.get().asFile.path,
        )
    }
}

// The jars in lib/ and the Java runtime in runtime/: what the archive, the npm and the NuGet packages carry
val toolDistribution by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Assembles the CLI jars and its Java runtime for the platform the build runs on"
    // jlink writes read-only files, which a later copy over them could not replace
    eachFile { permissions { user { write = true } } }
    from(tasks.jar) { into("lib") }
    from(configurations.runtimeClasspath) { into("lib") }
    from(runtimeImage) {
        into("runtime")
        filesMatching(listOf("bin/**", "lib/jspawnhelper")) { permissions { unix("rwxr-xr-x") } }
    }
    into(layout.buildDirectory.dir("tool-distribution"))
}

val cliArchive by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packs the CLI with its own Java runtime for the platform the build runs on"
    val rootFolder = "bpmn-to-code-$version-$cliPlatform"
    archiveFileName.set("$rootFolder.zip")
    destinationDirectory.set(layout.buildDirectory.dir("cli-archive"))
    from(toolDistribution) {
        into(rootFolder)
        useFileSystemPermissions()
    }
    from(layout.projectDirectory.dir("src/launcher")) {
        into("$rootFolder/bin")
        filePermissions { unix("rwxr-xr-x") }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("bpmn.models.dir", rootProject.file("shared/bpmn").absolutePath)
}

private val coverageExclusions = listOf(
    "**/cli/MainKt*", // process entry point, only hands the arguments to BpmnToCodeCli and exits
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

fun platformOf(osName: String, osArch: String): String {
    val os = when {
        osName.startsWith("Mac") -> "darwin"
        osName.startsWith("Windows") -> "win32"
        else -> "linux"
    }
    val arch = if (osArch == "aarch64" || osArch == "arm64") "arm64" else "x64"
    return "$os-$arch"
}

// npm delivery: @miragon/bpmn-to-code-<platform> carries the tool with the same runtime image as the archive,
// @miragon/bpmn-to-code starts it. Each platform is built on a machine of that platform.
val npmVersion = version.toString()
val npmDir = layout.buildDirectory.dir("npm")

val npmPlatformPackage by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Assembles the npm package with the tool and its Java runtime for the platform the build runs on"
    val (os, cpu) = cliPlatform.split("-")
    val properties = mapOf("platform" to cliPlatform, "os" to os, "cpu" to cpu, "version" to npmVersion)
    inputs.properties(properties)
    from(rootProject.file("packages/npm/platform/package.json.template")) {
        rename { "package.json" }
        expand(properties)
    }
    from(toolDistribution)
    into(npmDir.map { it.dir("platform/$cliPlatform") })
}

val npmMainPackage by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Assembles the npm package that starts the tool of the installed platform package"
    val packageVersion = npmVersion
    inputs.property("version", packageVersion)
    from(rootProject.file("packages/npm/bpmn-to-code"))
    filesMatching("package.json") { filter { line -> line.replace("0.0.0-development", packageVersion) } }
    into(npmDir.map { it.dir("bpmn-to-code") })
}
