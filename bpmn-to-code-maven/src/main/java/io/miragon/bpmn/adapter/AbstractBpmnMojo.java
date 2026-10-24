package io.miragon.bpmn.adapter;

import io.miragon.bpmn.domain.shared.ProcessEngine;
import java.io.File;
import java.nio.file.Path;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * The parameters every BPMN goal needs: where the models are and which engine they target.
 * Every parameter is required, as in Gradle; relative paths resolve against the project directory.
 */
public abstract class AbstractBpmnMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project.basedir}", readonly = true)
	private File projectDirectory;

	/**
	 * The directory filePattern is resolved against.
	 */
	@Parameter(property = "baseDir")
	private String baseDir;

	/**
	 * Pattern for locating the BPMN files, relative to baseDir.
	 */
	@Parameter(property = "filePattern")
	private String filePattern;

	/**
	 * Target process-engine of the BPMN files.
	 * Valid values: ZEEBE, CAMUNDA_7, OPERATON.
	 */
	@Parameter(property = "processEngine")
	private String processEngine;

	protected String baseDir() throws MojoFailureException {
		return projectPath("baseDir", baseDir);
	}

	protected String filePattern() throws MojoFailureException {
		return required("filePattern", filePattern);
	}

	protected ProcessEngine processEngine() throws MojoFailureException {
		return EnumParameter.parse("processEngine", processEngine, ProcessEngine.class);
	}

	protected String projectPath(String name, String value) throws MojoFailureException {
		Path path = Path.of(required(name, value));
		Path projectRoot = projectDirectory == null ? Path.of("").toAbsolutePath() : projectDirectory.toPath();
		return projectRoot.resolve(path).normalize().toString();
	}

	protected static String required(String name, String value) throws MojoFailureException {
		if (value == null || value.isBlank()) {
			throw new MojoFailureException(name + " is required");
		}
		return value;
	}
}
