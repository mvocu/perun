package cz.metacentrum.perun.engine.scheduling.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public abstract class AbstractWorker {
	private final static Logger log = LoggerFactory.getLogger(AbstractWorker.class);
	private File directory;
	private Integer returnCode = -1;
	private String stdout = null;
	private String stderr = null;

	public File getDirectory() {
		return directory;
	}

	public void setDirectory(File directory) {
		this.directory = directory;
	}

	public Integer getReturnCode() {
		return returnCode;
	}

	public String getStdout() {
		return stdout;
	}

	public String getStderr() {
		return stderr;
	}

	protected void execute(ProcessBuilder pb) throws InterruptedException, IOException {
		log.debug("The directory for the worker will be [{}]", getDirectory());
		if (getDirectory() != null) {
			// set path relative to current working dir
			pb.directory(getDirectory());
		}

		Process process = pb.start();

		StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream());
		StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream());

		errorGobbler.start();
		outputGobbler.start();

		returnCode = process.waitFor();

		while (errorGobbler.isAlive() || outputGobbler.isAlive()) Thread.sleep(50);

		stderr = errorGobbler.getSb();
		stdout = outputGobbler.getSb();
	}
}
