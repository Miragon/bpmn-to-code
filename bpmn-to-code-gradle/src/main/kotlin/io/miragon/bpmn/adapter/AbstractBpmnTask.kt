package io.miragon.bpmn.adapter

import io.miragon.bpmn.domain.shared.ProcessEngine
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.work.DisableCachingByDefault

/**
 * The inputs every BPMN task needs. Gradle reads each `@Input` before the task runs, so one that isn't set
 * fails the build by name ("property 'processEngine' doesn't have a configured value").
 */
@DisableCachingByDefault(because = "BPMN tasks read files that can change at any time without the plugin knowing about it")
abstract class AbstractBpmnTask : DefaultTask() {

    @get:Input
    abstract val baseDir: Property<String>

    @get:Input
    abstract val filePattern: Property<String>

    @get:Input
    abstract val processEngine: Property<ProcessEngine>
}
