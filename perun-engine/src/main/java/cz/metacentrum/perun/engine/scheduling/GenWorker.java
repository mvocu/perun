package cz.metacentrum.perun.engine.scheduling;


import cz.metacentrum.perun.engine.scheduling.EngineWorker;
import cz.metacentrum.perun.taskslib.model.Task;

import java.io.File;

public interface GenWorker extends EngineWorker<Task> {
	Task getTask();

	void setDirectory(File directory);

	File getDirectory();

	@Override
	Task call() throws Exception;

	Integer getId();
}
