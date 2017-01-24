package cz.metacentrum.perun.dispatcher.scheduling.impl;

import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.core.api.exceptions.IllegalArgumentException;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueue;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueuePool;
import cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.model.ExecService;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.model.Task.TaskStatus;
import cz.metacentrum.perun.taskslib.model.TaskSchedule;
import cz.metacentrum.perun.taskslib.service.TaskManager;
import cz.metacentrum.perun.taskslib.service.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.DelayQueue;

/**
 * This class groups together Tasks of all statuses from Dispatcher, and allows basic operations on them.
 * These include creating, persisting, modifying, deleting and finding Tasks.
 */
@org.springframework.stereotype.Service("schedulingPool")
public class SchedulingPoolImpl implements SchedulingPool {

	private final static Logger log = LoggerFactory.getLogger(SchedulingPoolImpl.class);

	private final Map<Integer, DispatcherQueue> dispatchersByTaskId = new HashMap<>();
	@Autowired
	private DelayQueue<TaskSchedule> waitingTasksQueue;
	@Autowired
	private DelayQueue<TaskSchedule> waitingForcedTasksQueue;
	@Autowired
	private Properties dispatcherPropertiesBean;
	@Autowired
	private TaskStore taskStore;
	@Autowired
	private TaskManager taskManager;
	@Autowired
	private DispatcherQueuePool dispatcherQueuePool;

	public SchedulingPoolImpl() {
	}

	public SchedulingPoolImpl(Properties dispatcherPropertiesBean, TaskStore taskStore,
	                          TaskManager taskManager, DispatcherQueuePool dispatcherQueuePool) {
		this.dispatcherPropertiesBean = dispatcherPropertiesBean;
		this.taskStore = taskStore;
		this.taskManager = taskManager;
		this.dispatcherQueuePool = dispatcherQueuePool;
	}

	@Override
	public Task getTask(int id) {
		return taskStore.getTask(id);
	}

	@Override
	public Task getTask(Facility facility, ExecService execService) {
		return taskStore.getTask(facility, execService);
	}

	@Override
	public int getSize() {
		return taskStore.getSize();
	}

	@Override
	public Task addToPool(Task task) throws TaskStoreException {
		return taskStore.addToPool(task);
	}

	@Override
	public List<Task> getAllTasks() {
		return taskStore.getAllTasks();
	}

	@Override
	public List<Task> getTasksWithStatus(TaskStatus... status) {
		return taskStore.getTasksWithStatus(status);
	}

	@Override
	public Task removeTask(Task task) throws TaskStoreException {
		return taskStore.removeTask(task);
	}

	/**
	 * Adds supplied Task into DelayQueue while also resetting its updated status.
	 * Used for Tasks with sources that were updated before Tasks sending to Engine.
	 *
	 * @param resetUpdated If true, Tasks sourceUpdated parameter is set to false.
	 *                     For other params see AddTaskSchedule(Task, int).
	 */
	public void addTaskSchedule(Task task, int delayCount, boolean resetUpdated) {
		if (resetUpdated) {
			task.setSourceUpdated(false);
		}
		addTaskSchedule(task, delayCount);
	}

	/**
	 * Adds supplied Task into DelayQueue comprised of other Tasks waiting to be sent to Engine.
	 *
	 * @param task       Task which will be added to the queue.
	 * @param delayCount Time for which the Task will be waiting in the queue.
	 *                   If the supplied value is lower or equal to 0, the value is read from propertyBean.
	 */
	public void addTaskSchedule(Task task, int delayCount) {
		long newTaskDelay = Long.parseLong(dispatcherPropertiesBean.getProperty("dispatcher.new_task.delay.time"));
		if (delayCount < 0) {
			delayCount = Integer.parseInt(dispatcherPropertiesBean.getProperty("dispatcher.new_task.delay.count"));
		}
		TaskSchedule schedule = new TaskSchedule(newTaskDelay, task);
		schedule.setBase(System.currentTimeMillis());
		schedule.setDelayCount(delayCount);
		boolean added = false;
		if (schedule.getTask().isPropagationForced()) {
			added = waitingForcedTasksQueue.add(schedule);
		} else {
			added = waitingTasksQueue.add(schedule);
		}
		if (!added) {
			log.error("{}, could not be added to waiting queue", schedule);
		} else {
			log.debug("{}, was added to waiting queue", schedule);
		}
	}

	/**
	 * Adds Task and associated dispatcherQueue into scheduling pools internal maps and also to the database.
	 *
	 * @param task            Task which will be added and persisted.
	 * @param dispatcherQueue dispatcherQueue associated with the Task which will be added and persisted.
	 * @return Number of Tasks in the pool.
	 * @throws InternalErrorException Thrown if the Task could not be persisted.
	 */
	@Override
	public int addToPool(Task task, DispatcherQueue dispatcherQueue)
			throws InternalErrorException, TaskStoreException {
		int engineId = (dispatcherQueue == null) ? -1 : dispatcherQueue.getClientID();
		if (task.getId() == 0) {
			if (getTask(task.getFacility(), task.getExecService()) == null) {
				log.debug("Adding new task to pool {}", task);

				try {
					log.debug("TESTSTR -> Scheduling new task");
					int id = taskManager.scheduleNewTask(task, engineId);
					log.debug("TESTSTR -> New task scheduled, id is {}", id);
					task.setId(id);
				} catch (InternalErrorException e) {
					log.error("Error storing task {} into database: {}", task, e.getMessage());
					throw new InternalErrorException("Could not assign id to newly created task {}", e);
				}
			} else {
				try {
					log.debug("TESTSTR -> Getting existing task");
					Task existingTask = taskManager.getTaskById(task.getId());
					if (existingTask == null) {
						log.debug("TESTSTR -> Scheduling new task");
						taskManager.scheduleNewTask(task, engineId);
					} else {
						log.debug("TESTSTR -> Updating existing task");
						taskManager.updateTask(task);
					}
				} catch (InternalErrorException e) {
					log.error("Error storing task {} into database: {}", task, e.getMessage());
				}
			}
		}

		addToPool(task);
		dispatchersByTaskId.put(task.getId(), dispatcherQueue);
		log.debug("TESTSTR -> Successfully added Task with id {}", task.getId());
		return getSize();
	}

	@Override
	public Task removeTask(int id) throws TaskStoreException {
		return taskStore.removeTask(id);
	}

	@Override
	public DispatcherQueue getQueueForTask(Task task)
			throws InternalErrorException {
		if (task == null) {
			log.error("Supplied Task is null.");
			throw new IllegalArgumentException("Task cannot be null");
		}
		DispatcherQueue entry = dispatchersByTaskId.get(task.getId());
		if (entry == null) {
			throw new InternalErrorException("no such task");
		}
		return entry;
	}


	@Override
	public List<Task> getTasksForEngine(int clientID) {
		List<Task> result = new ArrayList<Task>();
		for (Map.Entry<Integer, DispatcherQueue> entry : dispatchersByTaskId.entrySet()) {
			if (entry.getValue() != null && clientID == entry.getValue().getClientID()) {
				result.add(getTask(entry.getKey()));
			}
		}
		return result;
	}

	@Override
	public void clear() {
		taskStore.clear();
		dispatchersByTaskId.clear();
		waitingTasksQueue.clear();
		waitingForcedTasksQueue.clear();
	}

	/**
	 * Loads Tasks persisted in the database into internal scheduling pool maps.
	 */
	@Override
	public void reloadTasks() {
		log.debug("Going to reload tasks from database...");
		this.clear();
		for (Pair<Task, Integer> pair : taskManager.listAllTasksAndClients()) {
			Task task = pair.getLeft();
			task.setStatus(TaskStatus.WAITING);
			DispatcherQueue queue = dispatcherQueuePool.getDispatcherQueueByClient(pair.getRight());
			try {
				addToPool(task, queue);
			} catch (InternalErrorException | TaskStoreException e) {
				log.error("Inserting Task {} and Queue {} into SchedulingPool failed,so the Task will be lost.");
			}
			addTaskSchedule(task, 0);
			log.debug("Added task {} belonging to queue {}", task.toString(), pair.getRight());
		}
	}

	@Override
	public void setQueueForTask(Task task, DispatcherQueue queueForTask) throws InternalErrorException {
		Task found = getTask(task.getId());
		if (found == null) {
			throw new InternalErrorException("no task by id " + task.getId());
		} else {
			dispatchersByTaskId.put(task.getId(), queueForTask);
		}
		// if queue is removed, set -1 to task as it's done on task creation if queue is null
		int queueId = (queueForTask != null) ? queueForTask.getClientID() : -1;
		//taskManager.updateTaskEngine(task, queueId);
	}

	public void setDispatcherPropertiesBean(Properties dispatcherPropertiesBean) {
		this.dispatcherPropertiesBean = dispatcherPropertiesBean;
	}
}
