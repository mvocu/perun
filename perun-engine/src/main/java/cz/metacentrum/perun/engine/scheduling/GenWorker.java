package cz.metacentrum.perun.engine.scheduling;

import cz.metacentrum.perun.taskslib.model.Task;

import java.io.File;

/**
 * Worker used to execute Tasks GEN script.
 */
public interface GenWorker extends EngineWorker<Task> {
	Task getTask();

	File getDirectory();

	void setDirectory(File directory);

	@Override
	Task call() throws Exception;

	Integer getId();
}
