package cz.metacentrum.perun.dispatcher.scheduling;

import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueue;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.service.TaskStore;

import java.util.List;

/**
 * In-memory pool of all Tasks. On application start, all Tasks are reloaded from DB.
 *
 * New Tasks are added by EventProcessor, existing Tasks are updated. Change in Task state can
 * be also caused by PropagationMaintainer (closing propagation cycle).
 *
 * Tasks can be then
 * pushed to waitingTasksQueue
 *
 * Allows association of Tasks with Engines (DispatcherQueue).
 *
 * @see cz.metacentrum.perun.dispatcher.processing.EventProcessor
 * @see cz.metacentrum.perun.dispatcher.scheduling.PropagationMaintainer
 *
 * @author Michal Voců
 * @author Michal Babacek
 * @author David Šarman
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
public interface SchedulingPool extends TaskStore {

	/**
	 * Add Task associated with some engine (or null) to DB and internal scheduling pool.
	 *
	 * @param task Task to be added
	 * @param dispatcherQueue Queue for some Engine or null
	 * @return Current size of pool after adding
	 * @throws InternalErrorException When implementation fails.
	 * @throws TaskStoreException When Task can't be added.
	 */
	int addToPool(Task task, DispatcherQueue dispatcherQueue) throws InternalErrorException, TaskStoreException;

	/**
	 * Adds supplied Task into DelayQueue while also resetting its source updated status.
	 *
	 * Forced Tasks will have delay set to 0, other will use system property: "dispatcher.new_task.delay.time"
	 * It's advised for forced Tasks to also set delayCount to 0.
	 *
	 * Always retrieve Service/Facility from DB to cross-check actual data.
	 * Check if Service/Facility exists and has connection and is not blocked.
	 *
	 * If check fails, Task is not scheduled, if passes, status is changed to WAITING.
	 *
	 * @see #scheduleTask(Task, int, boolean)
	 * @param task Task to schedule propagation for
	 * @param delayCount How long to wait before sending to engine
	 */
	void scheduleTask(Task task, int delayCount);

	/**
	 * Adds supplied Task into DelayQueue while also resetting its source updated status.
	 * Used when source was updated before sending Task to Engine (Task was already in a delay queue).
	 *
	 * Forced Tasks will have delay set to 0, other will use system property: "dispatcher.new_task.delay.time"
	 * It's advised for forced Tasks to also set delayCount to 0.
	 *
	 * Always retrieve Service/Facility from DB to cross-check actual data.
	 * Check if Service/Facility exists and has connection and is not blocked.
	 *
	 * If check fails, Task is not scheduled, if passes, status is changed to WAITING.
	 *
	 * @see #scheduleTask(Task, int)
	 * @param task Task to schedule propagation for
	 * @param delayCount How long to wait before sending to engine
	 * @param resetUpdated If true, Tasks sourceUpdated parameter is set to false.
	 */
	void scheduleTask(Task task, int delayCount, boolean resetUpdated);

	/**
	 * Loads Tasks persisted in the database into internal scheduling pool maps.
	 * Immediately restart propagation of previously processing Tasks.
	 * Error and Done Tasks might be reschedule later by PropagationMaintainer.
	 */
	void reloadTasks();

	/**
	 * Clear all in-memory state of Tasks. Called during reloading of Tasks from DB.
	 */
	void clear();

	/**
	 * Return string representation of pool content like "TaskStatus = tasks count" for each TaskStatus.
	 *
	 * @return String representation of pool content
	 */
	String getReport();

	DispatcherQueue getQueueForTask(Task task) throws InternalErrorException;

	void setQueueForTask(Task task, DispatcherQueue queueForTask) throws InternalErrorException;

	List<Task> getTasksForEngine(int clientID);

}
