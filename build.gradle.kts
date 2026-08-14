import info.solidsoft.gradle.pitest.PitestPluginExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.pitest) apply false
}

val pitestCoreVersion = libs.versions.pitestCore.get()
val pitestJunit5Version = libs.versions.pitestJunit5.get()

allprojects {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

subprojects {

    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "io.github.usefulness.ktlint-gradle-plugin")

    configure<DetektExtension> {
        config.setFrom("$rootDir/config/detekt/detekt.yml")
        buildUponDefaultConfig = true
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn("detekt")
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    plugins.withId("jacoco") {
        tasks.withType<Test>().configureEach {
            finalizedBy(tasks.named("jacocoTestReport"))
        }

        tasks.withType<JacocoReport>().configureEach {
            dependsOn(tasks.withType<Test>())
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }

        tasks.withType<JacocoCoverageVerification>().configureEach {
            dependsOn(tasks.withType<AbstractCompile>())
            violationRules {
                rule {
                    element = "CLASS"
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = "0.75".toBigDecimal()
                    }
                }
            }
        }
    }

    plugins.withId("info.solidsoft.pitest") {
        configure<PitestPluginExtension> {
            pitestVersion.set(pitestCoreVersion)
            junit5PluginVersion.set(pitestJunit5Version)

            targetClasses.set(listOf("io.miragon.*"))
            outputFormats.set(listOf("HTML", "XML"))
            timestampedReports.set(false)
            threads.set(Runtime.getRuntime().availableProcessors())
            avoidCallsTo.set(
                listOf(
                    "kotlin.jvm.internal.Intrinsics",
                    "kotlin.io.CloseableKt",
                    "io.github.oshai.kotlinlogging",
                    "java.util.logging",
                    "org.slf4j",
                    "org.apache.log4j",
                    "org.apache.commons.logging",
                ),
            )

            excludedClasses.set(
                listOf(
                    "*\$DefaultImpls",
                    "*\$Companion",
                    "*\$WhenMappings",
                    "*\$\$serializer",
                    "*\$\$inlined\$*",
                    "io.miragon.bpmn.domain.shared.*",
                    "io.miragon.bpmn.domain.validation.model.*",
                    "io.miragon.bpmn.application.port.*",
                ),
            )
        }
    }
}
