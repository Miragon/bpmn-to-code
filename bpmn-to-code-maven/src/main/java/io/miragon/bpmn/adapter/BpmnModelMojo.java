package io.miragon.bpmn.adapter;

import io.miragon.bpmn.adapter.inbound.CreateProcessApiFilesystemPlugin;
import io.miragon.bpmn.domain.BpmnFileResult;
import io.miragon.bpmn.domain.shared.OutputLanguage;
import io.miragon.bpmn.domain.shared.ProcessEngine;
import io.miragon.bpmn.domain.validation.model.ValidationConfig;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Maven Mojo for generating type-safe API definitions from BPMN process models
 */
@Mojo(
		name = "generate-bpmn-api",
		defaultPhase = LifecyclePhase.NONE,
		requiresProject = false
)
public class BpmnModelMojo extends AbstractBpmnMojo {
	
	/**
	 * Output folder path where the generated API code will be written.
	 * Defaults to "src/main/kotlin".
	 */
	@Parameter(property = "outputFolderPath", defaultValue = "src/main/kotlin")
	private String outputFolderPath;
	
	/**
	 * Package path for the generated API code.
	 * Defaults to "de.emaarco.generated".
	 */
	@Parameter(property = "packagePath", defaultValue = "de.emaarco.generated")
	private String packagePath;
	
	/**
	 * Output language for code generation.
	 * Valid values: KOTLIN, JAVA, CSHARP (experimental). Defaults to KOTLIN.
	 */
	@Parameter(property = "outputLanguage", defaultValue = "KOTLIN")
	private String outputLanguage;
	
	/**
	 * Default constructor for maven purposes
	 */
	@SuppressWarnings("unused")
	public BpmnModelMojo() {
	}
	
	/**
	 * Executes the BPMN API generation process
	 */
	@Override
	public void execute() throws MojoFailureException {
		CreateProcessApiFilesystemPlugin plugin = new CreateProcessApiFilesystemPlugin();
		OutputLanguage language = EnumParameter.parse("outputLanguage", outputLanguage, OutputLanguage.class);
		ProcessEngine engine = processEngine();
		List<BpmnFileResult> results = plugin.execute(baseDir, filePattern, outputFolderPath, packagePath, language, engine, new ValidationConfig());
		if (results.isEmpty()) {
			getLog().info("No BPMN models found");
			return;
		}
		for (BpmnFileResult result : results) {
			String files = String.join(", ", result.getSourceFiles());
			getLog().info("  Generated: " + result.getProcessId() + " (from " + files + ")");
		}
		getLog().info("BPMN models generated successfully (" + results.size() + " models)");
	}
	
}
