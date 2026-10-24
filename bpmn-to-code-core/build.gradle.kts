plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.pitest)
    jacoco
}

group = "io.miragon"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.bpmnmodel)
    implementation(libs.bundles.codegen)

    api(libs.slf4jApi)
    api(libs.kotlinLogging)
    testImplementation(libs.bundles.testing)
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(project(":bpmn-to-code-runtime"))
    testRuntimeOnly(libs.junitPlatformLauncher)
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("shared"))
    }
}

private val runtimeTestSources = rootProject.file("bpmn-to-code-runtime/src/test")

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("golden.update", System.getProperty("golden.update") ?: "false")
    systemProperty("runtime.test.sources", runtimeTestSources.absolutePath)
    inputs.files(
        runtimeTestSources.resolve("kotlin/io/miragon/bpmn/runtime/path/example/BikeLeasingProcessApi.kt"),
        runtimeTestSources.resolve("java/io/miragon/bpmn/runtime/example/BikeLeasingProcessApi.java"),
    )
}

private val coverageExclusions = listOf(
    "**/domain/shared/**",
    "**/domain/validation/model/**",
    "**/adapter/outbound/engine/**/*Constants*",
    "**/application/port/**",
    $$"**/*$DefaultImpls*",
    $$"**/*$Companion*",
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
    excludedClasses.addAll("io.miragon.bpmn.adapter.outbound.engine.*Constants*")
    mutationThreshold.set(80)
    jvmArgs.add("-Druntime.test.sources=${runtimeTestSources.absolutePath}")
}
