plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.kotlinxSerializationJson)

    api(libs.slf4jApi)
    api(libs.kotlinLogging)
    testImplementation(libs.bundles.testing)
    testImplementation(kotlin("compiler-embeddable"))
    testImplementation(kotlin("reflect"))
    testImplementation(libs.jsonSchemaValidator)
    testImplementation(project(":bpmn-to-code-runtime"))
    testRuntimeOnly(libs.junitPlatformLauncher)
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("shared"))
        resources.srcDir(rootProject.file("docs/public/schema"))
    }
}

tasks.processResources {
    from(rootProject.file("docs/public/schema")) {
        into("META-INF/bpmn-to-code/schema")
    }
}

private val runtimeTestSources = rootProject.file("bpmn-to-code-runtime/src/test")
private val csharpRuntime = rootProject.file("packages/nuget/src/Miragon.BpmnToCode.Runtime")
private val csharpRuntimeTests = rootProject.file("packages/nuget/test/Miragon.BpmnToCode.Runtime.Tests")
private val typescriptRuntime = rootProject.file("packages/npm/bpmn-to-code-runtime")
private val runtimeLocations = mapOf(
    "runtime.test.sources" to runtimeTestSources,
    "csharp.runtime" to csharpRuntime,
    "csharp.runtime.tests" to csharpRuntimeTests,
    "typescript.runtime" to typescriptRuntime,
)

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("golden.update", System.getProperty("golden.update") ?: "false")
    runtimeLocations.forEach { (name, location) -> systemProperty(name, location.absolutePath) }
    inputs.files(
        runtimeTestSources.resolve("kotlin/io/miragon/bpmn/runtime/path/example/BikeLeasingProcessApi.kt"),
        runtimeTestSources.resolve("java/io/miragon/bpmn/runtime/example/BikeLeasingProcessApi.java"),
    )
    inputs.files(fileTree(csharpRuntime) { include("**/*.cs") })
    inputs.files(fileTree(csharpRuntimeTests) { include("Example/*.cs") })
    inputs.files(fileTree(typescriptRuntime) { include("src/**/*.ts", "test/example/*.ts") })
}

private val coverageExclusions = listOf(
    "**/domain/shared/**",
    "**/domain/validation/model/**",
    "**/adapter/outbound/engine/**/*Constants*",
    "**/adapter/outbound/json/model/**",
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
    excludedClasses.addAll(
        "io.miragon.bpmn.adapter.outbound.engine.*Constants*",
        "io.miragon.bpmn.adapter.outbound.json.model.*",
    )
    excludedTestClasses.add("io.miragon.bpmn.adapter.outbound.codegen.surface.GeneratedApiSurfaceTest")
    mutationThreshold.set(80)
    jvmArgs.addAll(runtimeLocations.map { (name, location) -> "-D$name=${location.absolutePath}" })
}
