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
            "--add-modules", "java.base,java.xml,java.compiler,java.logging",
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--compress=zip-6",
            "--output", outputDir.get().asFile.path,
        )
    }
}

val cliArchive by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Packs the CLI with its own Java runtime for the platform the build runs on"
    val rootFolder = "bpmn-to-code-$version-$cliPlatform"
    archiveFileName.set("$rootFolder.zip")
    destinationDirectory.set(layout.buildDirectory.dir("cli-archive"))
    from(tasks.jar) { into("$rootFolder/lib") }
    from(configurations.runtimeClasspath) { into("$rootFolder/lib") }
    from(runtimeImage) {
        into("$rootFolder/runtime")
        filesMatching(listOf("bin/**", "lib/jspawnhelper")) { permissions { unix("rwxr-xr-x") } }
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
