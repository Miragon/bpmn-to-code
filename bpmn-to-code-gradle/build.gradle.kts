plugins {
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gradlePluginPublish)
    jacoco
}

group = "io.miragon"
version = property("projectVersion").toString()

repositories {
    mavenLocal()
    mavenCentral()
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("shared"))
    }
}

val pluginVersionResourceDir = layout.buildDirectory.dir("generated/resources/plugin-version")

val generatePluginVersionResource by tasks.registering {
    val versionProvider = provider { version.toString() }
    val outputDir = pluginVersionResourceDir
    inputs.property("version", versionProvider)
    outputs.dir(outputDir)
    doLast {
        outputDir.get().file("bpmn-to-code-plugin.properties").asFile.apply {
            parentFile.mkdirs()
            writeText("version=${versionProvider.get()}\n")
        }
    }
}

sourceSets.main {
    resources.srcDir(generatePluginVersionResource)
}

dependencies {
    api(kotlin("stdlib"))
    api(libs.bpmnmodel)
    api(libs.bundles.codegen)
    api(libs.kotlinxSerializationJson)
    api(libs.slf4jApi)
    api(libs.kotlinLogging)
    compileOnly(project(":bpmn-to-code-core"))
    testImplementation(gradleTestKit())
    testImplementation(project(":bpmn-to-code-core"))
    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.jar {
    from(project(":bpmn-to-code-core").sourceSets.main.get().output)
}

tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    pluginClasspath.from(project(":bpmn-to-code-core").sourceSets.main.get().output)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    dependsOn("publishToMavenLocal")
    dependsOn(":bpmn-to-code-runtime:publishToMavenLocal")
    systemProperty("pluginVersion", version.toString())
    systemProperty("kotlinVersion", libs.versions.kotlin.get())
}

val isSnapshot = version.toString().endsWith("-SNAPSHOT")

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            groupId = "io.miragon"
            artifactId = "bpmn-to-code-gradle"
        }
    }
    repositories {
        mavenLocal()
        // The Gradle Plugin Portal cannot host SNAPSHOTs, so snapshot builds of the plugin
        // (including its auto-generated plugin-marker publication) are published to Central
        // Snapshots instead. Guarded so release versions never land here - the Portal stays
        // the release channel.
        if (isSnapshot) {
            maven {
                name = "centralSnapshots"
                url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                credentials {
                    username = providers.gradleProperty("mavenCentralUsername").orNull
                    password = providers.gradleProperty("mavenCentralPassword").orNull
                }
            }
        }
    }
}

gradlePlugin {
    website = "https://github.com/Miragon/bpmn-to-code"
    vcsUrl = "https://github.com/Miragon/bpmn-to-code"
    plugins {
        create("io.miragon.bpmn-to-code-gradle") {
            id = "io.miragon.bpmn-to-code-gradle"
            displayName = "bpmn-to-code"
            description =
                "Gradle plugin that bridges gaps between BPMN and code - fostering the creation of clean process-automation solutions"
            implementationClass = "io.miragon.bpmn.adapter.BpmnModelGeneratorPlugin"
            tags = setOf("bpmn", "codegen")
        }
    }
}
