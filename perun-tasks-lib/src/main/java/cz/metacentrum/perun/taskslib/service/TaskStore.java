package cz.metacentrum.perun.taskslib.service;

import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Service;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.model.Task;

import java.util.List;

/**
 * This interface describes basic Task storing functionality, where every Task is uniquely represented both by its ID,
 * and the Facility and ExecService it contains.
 */
public interface TaskStore {
	Task getTask(int id);
	Task getTask(Facility facility, Service service);
	int getSize();
	Task addToPool(Task task) throws TaskStoreException;
	List<Task> getAllTasks();
	List<Task> getTasksWithStatus(Task.TaskStatus... status);
	Task removeTask(Task task) throws TaskStoreException;
	Task removeTask(int id) throws TaskStoreException;
	void clear();
}
