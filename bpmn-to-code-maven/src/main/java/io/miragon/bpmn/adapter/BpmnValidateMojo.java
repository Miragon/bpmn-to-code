package io.miragon.bpmn.adapter;

import io.miragon.bpmn.adapter.inbound.ValidateBpmnFilesystemPlugin;
import io.miragon.bpmn.domain.shared.ProcessEngine;
import io.miragon.bpmn.domain.validation.model.ValidationConfig;
import io.miragon.bpmn.domain.validation.ValidationResult;
import io.miragon.bpmn.domain.validation.model.ValidationViolation;
import org.apache.maven.plugin.AbstractMojo;
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
public class BpmnValidateMojo extends AbstractMojo {

	/**
	 * The base directory for the plugin execution.
	 * Defaults to the current directory.
	 */
	@Parameter(property = "baseDir", defaultValue = ".")
	private String baseDir;

	/**
	 * Pattern for locating BPMN files to validate.
	 * Defaults to "src/main/resources/*.bpmn".
	 */
	@Parameter(property = "filePattern", defaultValue = "src/main/resources/*.bpmn")
	private String filePattern;

	/**
	 * Target process-engine for validation.
	 * Valid values: CAMUNDA_7, ZEEBE, OPERATON.
	 */
	@Parameter(property = "processEngine")
	private String processEngine;

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
		ProcessEngine engine = EnumParameter.parse("processEngine", processEngine, ProcessEngine.class);
		ValidationConfig config = new ValidationConfig(failOnWarning, disabledRules);
		ValidationResult result = plugin.execute(baseDir, filePattern, engine, config);

		for (ValidationViolation v : result.getWarnings()) {
			getLog().warn(v.describe());
		}
		for (ValidationViolation v : result.getErrors()) {
			getLog().error(v.describe());
		}

		if (result.hasFailures(failOnWarning)) {
			throw new MojoFailureException(result.getFailureSummary());
		}
		getLog().info("BPMN validation passed");
	}
}
