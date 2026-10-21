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

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("golden.update", System.getProperty("golden.update") ?: "false")
    systemProperty("runtime.test.sources", runtimeTestSources.absolutePath)
    inputs.files(
        runtimeTestSources.resolve("kotlin/io/miragon/bpmn/runtime/path/example/BikeLeasingProcessApi.kt"),
        runtimeTestSources.resolve("java/io/miragon/bpmn/runtime/example/BikeLeasingProcessApi.java"),
    )
}

// The poet and writer generation is no longer registered but kept until the FreeMarker generation has replaced it.
private val poetAndWriterGeneration = listOf(
    "**/adapter/outbound/codegen/builder/*/poet/**",
    "**/adapter/outbound/codegen/builder/csharp/writer/**",
    "**/adapter/outbound/codegen/builder/kotlin/Kotlin*",
    "**/adapter/outbound/codegen/builder/java/Java*",
    "**/adapter/outbound/codegen/builder/csharp/CSharpProcessApiBuilder*",
    "**/adapter/outbound/codegen/builder/csharp/CSharpSharedDefinitionsBuilder*",
)

private val coverageExclusions = listOf(
    "**/domain/shared/**",
    "**/domain/validation/model/**",
    "**/adapter/outbound/engine/**/*Constants*",
    "**/adapter/outbound/json/model/**",
    "**/application/port/**",
    $$"**/*$DefaultImpls*",
    $$"**/*$Companion*",
) + poetAndWriterGeneration

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
        "io.miragon.bpmn.adapter.outbound.codegen.builder.*.poet.*",
        "io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.writer.*",
        "io.miragon.bpmn.adapter.outbound.codegen.builder.kotlin.Kotlin*",
        "io.miragon.bpmn.adapter.outbound.codegen.builder.java.Java*",
        "io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpProcessApiBuilder*",
        "io.miragon.bpmn.adapter.outbound.codegen.builder.csharp.CSharpSharedDefinitionsBuilder*",
    )
    mutationThreshold.set(80)
    jvmArgs.add("-Druntime.test.sources=${runtimeTestSources.absolutePath}")
}
