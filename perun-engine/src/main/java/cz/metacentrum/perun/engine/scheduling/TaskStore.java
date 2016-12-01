package cz.metacentrum.perun.engine.scheduling;

import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.engine.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.model.ExecService;
import cz.metacentrum.perun.taskslib.model.Task;

import java.util.List;

public interface TaskStore {
	Task getTask(int id);
	Task getTask(Facility facility, ExecService execService);
	int getSize();
	Task addToPool(Task task) throws TaskStoreException;
	List<Task> getTasksWithStatus(Task.TaskStatus status);
	Task removeTask(Task task) throws TaskStoreException;
	Task removeTask(int id) throws TaskStoreException;
}
