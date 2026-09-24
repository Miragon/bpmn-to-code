package io.miragon.bpmn.adapter

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

class GradlePluginSmokeTest {

    private val kotlinVersion: String = requireNotNull(System.getProperty("kotlinVersion")) {
        "kotlinVersion system property must be set — run tests via Gradle"
    }

    @ParameterizedTest(name = "{0} / {1}")
    @CsvSource(
        "ZEEBE, KOTLIN, c8-subscribe-newsletter.bpmn",
        "CAMUNDA_7, KOTLIN, c7-subscribe-newsletter.bpmn",
        "OPERATON, KOTLIN, operaton-subscribe-newsletter.bpmn",
        "ZEEBE, JAVA, c8-subscribe-newsletter.bpmn",
    )
    fun `generateBpmnModelApi produces output files that compile`(
        engine: String,
        language: String,
        bpmnFile: String,
        @TempDir projectDir: File,
    ) {
        // given: a minimal Gradle project with the plugin applied and a BPMN resource
        val target = Target.of(language)
        writeProject(projectDir, engine, target, bpmnFile)

        // when: running the compile task (which depends on generateBpmnModelApi)
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(target.compileTask)
            .build()

        // then: both generation and compilation succeed
        assertThat(result.task(":generateBpmnModelApi")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":${target.compileTask}")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertGeneratedFiles(projectDir, target.extension)
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource("KOTLIN", "JAVA")
    fun `consumer code reads typed edges and node facets from the generated Flow`(language: String, @TempDir projectDir: File) {
        // given: a project whose own source navigates the generated API and reads facets
        val target = Target.of(language)
        writeProject(projectDir, "ZEEBE", target, "c8-subscribe-newsletter.bpmn")
        File(projectDir, target.consumerPath).apply { parentFile.mkdirs() }.writeText(target.consumerSource)

        // when: compiling the consumer against the generated API and the runtime
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(target.compileTask)
            .build()

        // then: every referenced member resolves
        assertThat(result.task(":${target.compileTask}")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    @Test
    fun `generateBpmnModelApi writes a cs file with Flow for CSHARP`(@TempDir projectDir: File) {
        // given: a JVM project (the plugin only wires itself when a JVM plugin is present) targeting C#
        writeProject(projectDir, "ZEEBE", Target.CSHARP, "c8-subscribe-newsletter.bpmn")

        // when: running generation only — the C# compile gate lives in core's CSharpCompilationTest
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateBpmnModelApi")
            .build()

        // then: the Process API .cs file carries the navigation, next to the shared definition files
        assertThat(result.task(":generateBpmnModelApi")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        val generated = assertGeneratedFiles(projectDir, ".cs")
        val processApi = generated.single { it.name == "NewsletterSubscriptionProcessApi.cs" }
        assertThat(processApi.readText()).contains("public static class Flow", "public static class Runtime")
        assertThat(generated.map { it.name }).contains("ServiceTasks.cs", "Messages.cs")
    }

    private fun writeProject(projectDir: File, engine: String, target: Target, bpmnFile: String) {
        val resourcesDir = File(projectDir, "src/main/resources").also { it.mkdirs() }
        val bpmnStream = requireNotNull(javaClass.classLoader.getResourceAsStream("bpmn/$bpmnFile"))
        File(resourcesDir, bpmnFile).writeBytes(bpmnStream.readBytes())
        File(projectDir, "settings.gradle").writeText("")

        val languagePlugin = if (target == Target.KOTLIN) "id 'org.jetbrains.kotlin.jvm' version '$kotlinVersion'" else "id 'java'"
        val srcDirBlock = if (target == Target.KOTLIN) "kotlin { srcDirs = ['build/generated'] }" else "java { srcDir 'build/generated' }"

        File(projectDir, "build.gradle").writeText(
            """
            plugins {
                $languagePlugin
                id 'io.miragon.bpmn-to-code-gradle'
            }
            repositories {
                mavenLocal()
                mavenCentral()
            }
            sourceSets {
                main {
                    $srcDirBlock
                }
            }
            tasks.named('${target.compileTask}') {
                dependsOn tasks.named('generateBpmnModelApi')
            }
            tasks.named('generateBpmnModelApi') {
                baseDir = projectDir.toString()
                filePattern = 'src/main/resources/*.bpmn'
                outputFolderPath = "${'$'}{projectDir}/build/generated"
                packagePath = 'io.miragon.smoketest'
                outputLanguage = io.miragon.bpmn.domain.shared.OutputLanguage.${target.name}
                processEngine = io.miragon.bpmn.domain.shared.ProcessEngine.$engine
            }
            """.trimIndent(),
        )
    }

    private fun assertGeneratedFiles(projectDir: File, extension: String): List<File> {
        val packageDir = File(projectDir, "build/generated/io/miragon/smoketest")
        assertThat(packageDir).isDirectory()
        val generatedFiles = requireNotNull(packageDir.listFiles()).toList()
        assertThat(generatedFiles).isNotEmpty()
        assertThat(generatedFiles).allSatisfy { file -> assertThat(file.isFile).isTrue() }
        assertThat(generatedFiles).allSatisfy { file -> assertThat(file.name).endsWith(extension) }
        return generatedFiles
    }

    private enum class Target(val compileTask: String, val extension: String, val consumerPath: String, val consumerSource: String) {
        KOTLIN(
            compileTask = "compileKotlin",
            extension = ".kt",
            consumerPath = "src/main/kotlin/io/miragon/smoketest/UsesApi.kt",
            consumerSource = """
                package io.miragon.smoketest

                import io.miragon.smoketest.NewsletterSubscriptionProcessApi.Flow

                object UsesApi {
                    fun describe(): String {
                        val edge = Flow.StartEventSubmitRegistrationForm.flows().flowSubmitToIncrementCounter
                        val condition: String? = edge.conditionExpression
                        val input = Flow.CallActivityAbortRegistration.Variables.SUBSCRIPTION_ID
                        val mapping = Flow.CallActivityAbortRegistration.Inputs.SUBSCRIPTION_ID
                        val timer = Flow.TimerEveryDay.timer
                        val host = Flow.TimerEveryDay.attachedTo
                        val called = Flow.CallActivityAbortRegistration.calledProcess
                        val jobType = Flow.ServiceTaskSendConfirmationMail.JOB_TYPE
                        return listOf(condition, edge.isDefault, edge.target.id, input, mapping, timer.timerValue, host.name, called, jobType).joinToString()
                    }
                }
            """.trimIndent(),
        ),
        JAVA(
            compileTask = "compileJava",
            extension = ".java",
            consumerPath = "src/main/java/io/miragon/smoketest/UsesApi.java",
            consumerSource = """
                package io.miragon.smoketest;

                import io.miragon.smoketest.NewsletterSubscriptionProcessApi.Flow;

                public final class UsesApi {
                    public static String describe() {
                        var edge = Flow.startEventSubmitRegistrationForm().flows().flowSubmitToIncrementCounter();
                        String condition = edge.getConditionExpression();
                        var input = Flow.CallActivityAbortRegistration.Variables.SUBSCRIPTION_ID;
                        var mapping = Flow.CallActivityAbortRegistration.Inputs.SUBSCRIPTION_ID;
                        var timer = Flow.timerEveryDay().timer;
                        var host = Flow.timerEveryDay().attachedTo();
                        var called = Flow.callActivityAbortRegistration().calledProcess;
                        String jobType = Flow.ServiceTaskSendConfirmationMail.JOB_TYPE;
                        return condition + edge.isDefault() + edge.getTarget().getId() + input + mapping + timer.getTimerValue() + host.getName() + called + jobType;
                    }
                }
            """.trimIndent(),
        ),
        CSHARP(compileTask = "compileJava", extension = ".cs", consumerPath = "", consumerSource = ""),
        ;

        companion object {
            fun of(language: String): Target = valueOf(language)
        }
    }
}
