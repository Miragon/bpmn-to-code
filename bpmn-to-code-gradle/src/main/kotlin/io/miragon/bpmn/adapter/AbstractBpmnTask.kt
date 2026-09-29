package io.miragon.bpmn.adapter

import io.miragon.bpmn.domain.shared.ProcessEngine
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.work.DisableCachingByDefault

/**
 * The inputs every BPMN task needs. Gradle reads each `@Input` before the task runs, so one that isn't set
 * fails the build by name ("lateinit property processEngine has not been initialized").
 */
@DisableCachingByDefault(because = "BPMN tasks read files that can change at any time without the plugin knowing about it")
abstract class AbstractBpmnTask : DefaultTask() {

    @Input
    lateinit var baseDir: String

    @Input
    lateinit var filePattern: String

    @Input
    lateinit var processEngine: ProcessEngine
}
