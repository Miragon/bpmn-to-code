plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("kapt")
}

group = "io.miragon"

// Internal-only module, run by hand: measures the generator of this build against a published release with JMH.
// The released Gradle plugin jar bundles bpmn-to-code-core, so it serves as the baseline generator.
// The benchmarks reach both generators reflectively and therefore compile against neither.
// JMH forks inherit the classpath of the run, so each run measures the generator it was started with.

val baselineVersion = providers.gradleProperty("baselineVersion").getOrElse("5.2.0")
val benchmarkMode = providers.gradleProperty("mode").getOrElse("warm")
val scenarioFilter = providers.gradleProperty("scenario").getOrElse("")
val resultsDir = layout.buildDirectory.dir("benchmark")
val harnessMainClass = "io.miragon.bpmn.benchmark.BenchmarkMainKt"

val baselineGenerator: Configuration by configurations.creating {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

val currentGenerator: Configuration by configurations.creating {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
}

sourceSets {
    main {
        resources.srcDir(rootProject.file("shared"))
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.jmhCore)
    implementation(libs.kotlinxSerializationJson)
    kapt(libs.jmhAnnotationProcessor)
    baselineGenerator("io.miragon:bpmn-to-code-gradle:$baselineVersion")
    baselineGenerator("io.miragon:bpmn-to-code-runtime:$baselineVersion")
    currentGenerator(project(":bpmn-to-code-core"))
    currentGenerator(project(":bpmn-to-code-runtime"))
}

fun resultFile(label: String): String = resultsDir.get().file("$label-$benchmarkMode.json").asFile.absolutePath

fun registerBenchmarkRun(name: String, label: String, generator: Configuration) = tasks.register<JavaExec>(name) {
    group = "benchmark"
    description = "Runs the $benchmarkMode benchmark against the $label generator."
    classpath = sourceSets.main.get().runtimeClasspath + generator
    mainClass.set(harnessMainClass)
    args(benchmarkMode, resultFile(label), scenarioFilter)
    maxHeapSize = "2g"
}

val benchmarkBaseline = registerBenchmarkRun("benchmarkBaseline", baselineVersion, baselineGenerator)
val benchmarkCurrent = registerBenchmarkRun("benchmarkCurrent", "current", currentGenerator)

benchmarkCurrent.configure {
    mustRunAfter(benchmarkBaseline)
}

tasks.register<JavaExec>("benchmark") {
    group = "benchmark"
    description = "Compares the generator of this build with bpmn-to-code $baselineVersion."
    dependsOn(benchmarkBaseline, benchmarkCurrent)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(harnessMainClass)
    args("compare", baselineVersion, resultFile(baselineVersion), resultFile("current"))
}
