package io.miragon.bpmn.adapter;

import io.miragon.bpmn.domain.shared.ProcessEngine;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * The parameters every BPMN goal needs: where the models are and which engine they target.
 */
public abstract class AbstractBpmnMojo extends AbstractMojo {

	/**
	 * The base directory for the plugin execution.
	 * Defaults to the current directory.
	 */
	@Parameter(property = "baseDir", defaultValue = ".")
	protected String baseDir;

	/**
	 * Pattern for locating the BPMN files.
	 * Defaults to "src/main/resources/*.bpmn".
	 */
	@Parameter(property = "filePattern", defaultValue = "src/main/resources/*.bpmn")
	protected String filePattern;

	/**
	 * Target process-engine of the BPMN files.
	 * Valid values: ZEEBE, CAMUNDA_7, OPERATON.
	 */
	@Parameter(property = "processEngine")
	private String processEngine;

	protected ProcessEngine processEngine() throws MojoFailureException {
		return EnumParameter.parse("processEngine", processEngine, ProcessEngine.class);
	}
}
