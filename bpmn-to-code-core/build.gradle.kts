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

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("golden.update", System.getProperty("golden.update") ?: "false")
}

private val coverageExclusions = listOf(
    "**/domain/shared/**",
    "**/domain/validation/model/**",
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
