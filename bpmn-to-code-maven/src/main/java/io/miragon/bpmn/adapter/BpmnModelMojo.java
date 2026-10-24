package io.miragon.bpmn.adapter;

import io.miragon.bpmn.adapter.inbound.CreateProcessApiFilesystemPlugin;
import io.miragon.bpmn.domain.BpmnFileResult;
import io.miragon.bpmn.domain.shared.OutputLanguage;
import io.miragon.bpmn.domain.shared.ProcessEngine;
import io.miragon.bpmn.domain.validation.model.ValidationConfig;
import java.util.List;
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
	 */
	@Parameter(property = "outputFolderPath")
	private String outputFolderPath;
	
	/**
	 * Package path for the generated API code.
	 */
	@Parameter(property = "packagePath")
	private String packagePath;
	
	/**
	 * Output language for code generation.
	 * Valid values: KOTLIN, JAVA, CSHARP (experimental), TYPESCRIPT (experimental).
	 */
	@Parameter(property = "outputLanguage")
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
		String baseDir = baseDir();
		String filePattern = filePattern();
		String outputFolder = projectPath("outputFolderPath", outputFolderPath);
		String packageName = required("packagePath", packagePath);
		List<BpmnFileResult> results = failingOnRejectedModels(
				() -> plugin.execute(baseDir, filePattern, outputFolder, packageName, language, engine, new ValidationConfig())
		);
		for (BpmnFileResult result : results) {
			getLog().info("  " + result.describe());
		}
		getLog().info(BpmnFileResult.Companion.summaryOf(results));
	}
	
}
