package cz.metacentrum.perun.engine.scheduling;

import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.taskslib.model.SendTask;

import java.io.File;


public interface SendWorker extends EngineWorker<SendTask>{
	SendTask getSendTask();

	File getDirectory();

	void setDirectory(File directory);

	@Override
	SendTask call() throws Exception;
}
