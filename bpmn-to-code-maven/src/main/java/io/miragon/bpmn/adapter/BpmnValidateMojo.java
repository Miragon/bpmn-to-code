package io.miragon.bpmn.adapter;

import io.miragon.bpmn.adapter.inbound.ValidateBpmnFilesystemPlugin;
import io.miragon.bpmn.domain.shared.ProcessEngine;
import io.miragon.bpmn.domain.validation.model.ValidationConfig;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Collections;
import java.util.Set;

/**
 * [Experimental] Maven Mojo for validating BPMN models against built-in rules without generating code.
 * This goal is experimental and may change in future releases.
 */
@Mojo(
		name = "validate-bpmn",
		defaultPhase = LifecyclePhase.NONE,
		requiresProject = false
)
public class BpmnValidateMojo extends AbstractBpmnMojo {

	/**
	 * Whether to treat warnings as failures.
	 * Defaults to false.
	 */
	@Parameter(property = "failOnWarning", defaultValue = "false")
	private boolean failOnWarning;

	/**
	 * Set of rule IDs to disable during validation.
	 */
	@Parameter(property = "disabledRules")
	private Set<String> disabledRules = Collections.emptySet();

	/**
	 * Default constructor for maven purposes
	 */
	@SuppressWarnings("unused")
	public BpmnValidateMojo() {
	}

	/**
	 * Executes BPMN validation
	 */
	@Override
	public void execute() throws MojoFailureException {
		getLog().warn("[EXPERIMENTAL] The 'validate-bpmn' goal is experimental and may change in future releases.");
		ValidateBpmnFilesystemPlugin plugin = new ValidateBpmnFilesystemPlugin();
		ProcessEngine engine = processEngine();
		ValidationConfig config = new ValidationConfig(failOnWarning, disabledRules);
		String baseDir = baseDir();
		String filePattern = filePattern();
		failingOnRejectedModels(() -> plugin.execute(baseDir, filePattern, engine, config));
		getLog().info("BPMN validation passed");
	}
}
