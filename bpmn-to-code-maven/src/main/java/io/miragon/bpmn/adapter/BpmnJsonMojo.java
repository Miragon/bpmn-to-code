package io.miragon.bpmn.adapter;

import io.miragon.bpmn.adapter.inbound.CreateProcessJsonFilesystemPlugin;
import io.miragon.bpmn.domain.shared.ProcessEngine;
import io.miragon.bpmn.domain.validation.model.ValidationConfig;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Maven Mojo for generating JSON representations of BPMN process models
 *
 * @deprecated The Process JSON will be removed in 7.0.
 */
@Deprecated
@Mojo(
		name = "generate-bpmn-json",
		defaultPhase = LifecyclePhase.NONE,
		requiresProject = false
)
public class BpmnJsonMojo extends AbstractBpmnMojo {

	@Parameter(property = "outputFolderPath", defaultValue = "src/main/resources/bpmn-json")
	private String outputFolderPath;

	/**
	 * Default constructor for maven purposes
	 */
	@SuppressWarnings("unused")
	public BpmnJsonMojo() {
	}

	@Override
	public void execute() throws MojoFailureException {
		getLog().warn("[DEPRECATED] The 'generate-bpmn-json' goal is deprecated and will be removed in 7.0. "
				+ "If you use the Process JSON, describe your use case in https://github.com/Miragon/bpmn-to-code/issues/206.");
		CreateProcessJsonFilesystemPlugin plugin = new CreateProcessJsonFilesystemPlugin();
		ProcessEngine engine = processEngine();
		plugin.execute(baseDir, filePattern, outputFolderPath, engine, new ValidationConfig());
		getLog().info("BPMN JSON files generated successfully");
	}

}
