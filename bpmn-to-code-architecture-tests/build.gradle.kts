plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.miragon"

repositories {
    mavenCentral()
}

// Internal-only module: it holds the project-wide architecture guardrails (hexagonal layering,
// module import boundaries) enforced with Konsist. It is not published and depends on no other
// module — Konsist reads the project's source files directly from disk via scopeFromProject().
dependencies {
    testImplementation(libs.bundles.testing)
    testImplementation(libs.konsist)
    testImplementation(kotlin("compiler-embeddable"))
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
