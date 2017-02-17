package cz.metacentrum.perun.dispatcher.scheduling;

import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueue;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.service.TaskStore;

import java.util.List;
import java.util.Properties;

/**
 * @author Michal Voců
 *         <p>
 *         Contains: - database of Tasks and their states - mapping of Tasks to
 *         engines (dispatcherQueue)
 */
public interface SchedulingPool extends TaskStore {

	/**
	 * Add Task to the waiting list.
	 *
	 * @param task
	 * @param dispatcherQueue
	 * @return
	 * @throws InternalErrorException
	 */
	int addToPool(Task task, DispatcherQueue dispatcherQueue)
			throws InternalErrorException, TaskStoreException;

	void addTaskSchedule(Task task, int delayCount);

	void addTaskSchedule(Task task, int delayCount, boolean resetUpdated);

	DispatcherQueue getQueueForTask(Task task) throws InternalErrorException;

	void setQueueForTask(Task task, DispatcherQueue queueForTask) throws InternalErrorException;

	List<Task> getTasksForEngine(int clientID);

	String getReport();

	void clear();

	void reloadTasks();

	void setDispatcherProperties(Properties dispatcherProperties);
}
