package io.miragon.bpmn.adapter

import io.miragon.bpmn.domain.shared.ProcessEngine
import org.gradle.api.DefaultTask
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

/**
 * The inputs every BPMN task needs. Gradle reads each `@Input` before the task runs, so one that isn't set
 * fails the build by name ("property 'processEngine' doesn't have a configured value").
 */
@DisableCachingByDefault(because = "Abstract base task, the generating subclasses opt into caching")
abstract class AbstractBpmnTask : DefaultTask() {

    @get:Input
    abstract val baseDir: Property<String>

    @get:Input
    abstract val filePattern: Property<String>

    @get:Input
    abstract val processEngine: Property<ProcessEngine>

    @get:Inject
    protected abstract val layout: ProjectLayout

    protected fun absolutePathOf(path: Property<String>): String = layout.projectDirectory.dir(path.get()).asFile.absolutePath
}
