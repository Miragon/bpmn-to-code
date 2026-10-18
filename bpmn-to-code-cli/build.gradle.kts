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
