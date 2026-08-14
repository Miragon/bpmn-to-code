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
    // Lets the codegen tests compile generated output for real (not just parse) against the runtime interfaces.
    testImplementation(project(":bpmn-to-code-runtime"))
    testRuntimeOnly(libs.junitPlatformLauncher)
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("shared"))
        // The published JSON schema, so ProcessJsonSchemaTest validates against it offline
        resources.srcDir(rootProject.file("docs/public/schema"))
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    // Lets `./gradlew test -Dgolden.update=true` rewrite the end-to-end JSON snapshots.
    systemProperty("golden.update", System.getProperty("golden.update") ?: "false")
}

private val coverageExclusions = listOf(
    "**/domain/shared/**",
    "**/domain/validation/model/**",
    // Constant holders: their `const val`s are inlined at the call site, so the object itself
    // never executes. Matched by name rather than by package so a move cannot silently re-include them.
    "**/adapter/outbound/engine/**/*Constants*",
    "**/adapter/outbound/json/model/**",
    "**/application/port/**",
    "**/*\$DefaultImpls*",
    "**/*\$Companion*",
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
    mutationThreshold.set(80)
}
