package cz.metacentrum.perun.engine.scheduling.impl;


import cz.metacentrum.perun.engine.model.Pair;
import cz.metacentrum.perun.taskslib.model.Task;

import java.io.File;
import java.io.IOException;

public abstract class AbstractWorker {
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
