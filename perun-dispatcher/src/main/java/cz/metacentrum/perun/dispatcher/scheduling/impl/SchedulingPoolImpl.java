package cz.metacentrum.perun.dispatcher.scheduling.impl;

import cz.metacentrum.perun.controller.service.GeneralServiceManager;
import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.core.api.Perun;
import cz.metacentrum.perun.core.api.PerunClient;
import cz.metacentrum.perun.core.api.PerunPrincipal;
import cz.metacentrum.perun.core.api.PerunSession;
import cz.metacentrum.perun.core.api.Service;
import cz.metacentrum.perun.core.api.exceptions.FacilityNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.IllegalArgumentException;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PrivilegeException;
import cz.metacentrum.perun.core.api.exceptions.ServiceNotExistsException;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueue;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueuePool;
import cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.model.Task.TaskStatus;
import cz.metacentrum.perun.taskslib.model.TaskSchedule;
import cz.metacentrum.perun.taskslib.service.TaskManager;
import cz.metacentrum.perun.taskslib.service.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.DelayQueue;

/**
 * Implementation of SchedulingPool.
 *
 * @see cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool
 *
 * @author Michal Voců
 * @author Michal Babacek
 * @author David Šarman
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
@org.springframework.stereotype.Service("schedulingPool")
public class SchedulingPoolImpl implements SchedulingPool {

	private final static Logger log = LoggerFactory.getLogger(SchedulingPoolImpl.class);

	private final Map<Integer, DispatcherQueue> dispatchersByTaskId = new HashMap<>();
	private PerunSession sess;

	private DelayQueue<TaskSchedule> waitingTasksQueue;
	private DelayQueue<TaskSchedule> waitingForcedTasksQueue;
	private Properties dispatcherProperties;
	private TaskStore taskStore;
	private TaskManager taskManager;
	private DispatcherQueuePool dispatcherQueuePool;
	private GeneralServiceManager generalServiceManager;
	private Perun perun;

	public SchedulingPoolImpl() {
	}

	public SchedulingPoolImpl(Properties dispatcherProperties, TaskStore taskStore,
	                          TaskManager taskManager, DispatcherQueuePool dispatcherQueuePool) {
		this.dispatcherProperties = dispatcherProperties;
		this.taskStore = taskStore;
		this.taskManager = taskManager;
		this.dispatcherQueuePool = dispatcherQueuePool;
	}


	// ----- setters -------------------------------------


	public DelayQueue<TaskSchedule> getWaitingTasksQueue() {
		return waitingTasksQueue;
	}

	@Autowired
	public void setWaitingTasksQueue(DelayQueue<TaskSchedule> waitingTasksQueue) {
		this.waitingTasksQueue = waitingTasksQueue;
	}

	public DelayQueue<TaskSchedule> getWaitingForcedTasksQueue() {
		return waitingForcedTasksQueue;
	}

	@Autowired
	public void setWaitingForcedTasksQueue(DelayQueue<TaskSchedule> waitingForcedTasksQueue) {
		this.waitingForcedTasksQueue = waitingForcedTasksQueue;
	}

	public Properties getDispatcherProperties() {
		return dispatcherProperties;
	}

	@Autowired
	public void setDispatcherProperties(Properties dispatcherProperties) {
		this.dispatcherProperties = dispatcherProperties;
	}

	public TaskStore getTaskStore() {
		return taskStore;
	}

	@Autowired
	public void setTaskStore(TaskStore taskStore) {
		this.taskStore = taskStore;
	}

	public TaskManager getTaskManager() {
		return taskManager;
	}

	@Autowired
	public void setTaskManager(TaskManager taskManager) {
		this.taskManager = taskManager;
	}

	public DispatcherQueuePool getDispatcherQueuePool() {
		return dispatcherQueuePool;
	}

	@Autowired
	public void setDispatcherQueuePool(DispatcherQueuePool dispatcherQueuePool) {
		this.dispatcherQueuePool = dispatcherQueuePool;
	}

	public GeneralServiceManager getGeneralServiceManager() {
		return generalServiceManager;
	}

	@Autowired
	public void setGeneralServiceManager(GeneralServiceManager generalServiceManager) {
		this.generalServiceManager = generalServiceManager;
	}

	public Perun getPerun() {
		return perun;
	}

	@Autowired
	public void setPerun(Perun perun) {
		this.perun = perun;
	}


	// ----- methods -------------------------------------


	@Override
	public Task getTask(int id) {
		return taskStore.getTask(id);
	}

	@Override
	public Task getTask(Facility facility, Service service) {
		return taskStore.getTask(facility, service);
	}

	@Override
	public int getSize() {
		return taskStore.getSize();
	}

	@Override
	public Task addTask(Task task) throws TaskStoreException {
		return taskStore.addTask(task);
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

	@Override
	public void scheduleTask(Task task, int delayCount, boolean resetUpdated) {
		if (resetUpdated) {
			task.setSourceUpdated(false);
		}
		scheduleTask(task, delayCount);
	}

	@Override
	public void scheduleTask(Task task, int delayCount) {

		// init session
		try {
			if (sess == null) {
				sess = perun.getPerunSession(new PerunPrincipal(
								dispatcherProperties.getProperty("perun.principal.name"),
								dispatcherProperties.getProperty("perun.principal.extSourceName"),
								dispatcherProperties.getProperty("perun.principal.extSourceType")),
						new PerunClient());
			}
		} catch (InternalErrorException e1) {
			log.error("Error establishing perun session to add task schedule: ", e1);
			return;
		}

		// check if service/facility exists

		boolean removeTask = false;

		try {
			Service service = perun.getServicesManager().getServiceById(sess, task.getServiceId());
			Facility facility = perun.getFacilitiesManager().getFacilityById(sess, task.getFacilityId());
			task.setService(service);
			task.setFacility(facility);
		} catch (ServiceNotExistsException e) {
			log.error("[{}] Task NOT added to waiting queue, service not exists: {}.", task.getId(), task);
			removeTask = true;
		} catch (FacilityNotExistsException e) {
			log.error("[{}] Task NOT added to waiting queue, facility not exists: {}.", task.getId(), task);
			removeTask = true;
		}  catch (InternalErrorException | PrivilegeException e) {
			log.error("[{}] {}", task.getId(), e);
		}

		if (!task.getService().isEnabled() || generalServiceManager.isServiceBlockedOnFacility(task.getService(), task.getFacility())) {
			log.error("[{}] Task NOT added to waiting queue, service is blocked: {}.", task.getId(), task);
			// do not change Task status or any other data !
			if (!removeTask) return;
		}

		try {
			List<Service> assignedServices = perun.getServicesManager().getAssignedServices(sess, task.getFacility());
			if (!assignedServices.contains(task.getService())) {
				log.debug("[{}] Task NOT added to waiting queue, service is not assigned to facility any more: {}.", task.getId(), task);
				if (!removeTask) return;
			}
		} catch (FacilityNotExistsException e) {
			removeTask = true;
			log.error("[{}] Task removed from database, facility no longer exists: {}.", task.getId(), task);
		} catch (InternalErrorException | PrivilegeException e) {
			log.error("[{}] Unable to check Service assignment to Facility: {}", task.getId(), e.getMessage());
		}

		if (removeTask) {
			// in memory task belongs to non existent facility/service - remove it and return
			try {
				removeTask(task);
				return;
			} catch (TaskStoreException e) {
				log.error("[{}] Unable to remove Task from pool: {}.", task.getId(), e);
				return;
			}
		}

		// create new schedule

		long newTaskDelay = 0;
		if (!task.isPropagationForced()) {
			// normal tasks are delayed
			newTaskDelay = Long.parseLong(dispatcherProperties.getProperty("dispatcher.new_task.delay.time"));
		}

		if (delayCount < 0) {
			delayCount = Integer.parseInt(dispatcherProperties.getProperty("dispatcher.new_task.delay.count"));
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
			// Task was not added, so it probably already is in queue, do not update status or timestamps
			log.error("[{}] Task could not be added to waiting queue: {}", task.getId(), schedule);
		} else {

			log.debug("[{}] Task was added to waiting queue: {}", task.getId(), schedule);

			// FIXME - should we also reset Task timestamps ??
			// FIXME - we should probably set "scheduled" timestamp and clear others

			// Task was planned for propagation, switch state.
			task.setStatus(TaskStatus.WAITING);
			taskManager.updateTask(task);

		}

	}

	/**
	 * Adds Task and associated dispatcherQueue into scheduling pools internal maps and also to the database.
	 *
	 * @param task            Task which will be added and persisted.
	 * @param dispatcherQueue dispatcherQueue associated with the Task which will be added and persisted.
	 * @return Number of Tasks in the pool.
	 * @throws InternalErrorException Thrown if the Task could not be persisted.
	 * @throws TaskStoreException
	 */
	@Override
	public int addToPool(Task task, DispatcherQueue dispatcherQueue) throws InternalErrorException, TaskStoreException {

		int engineId = (dispatcherQueue == null) ? -1 : dispatcherQueue.getClientID();
		if (task.getId() == 0) {
			if (getTask(task.getFacility(), task.getService()) == null) {
				try {
					int id = taskManager.scheduleNewTask(task, engineId);
					task.setId(id);
				} catch (InternalErrorException e) {
					log.error("Error storing task {} into database: {}", task, e.getMessage());
					throw new InternalErrorException("Could not assign id to newly created task {}", e);
				}
				log.debug("[{}] New Task stored in DB: {}", task.getId(), task);
			} else {
				try {
					Task existingTask = taskManager.getTaskById(task.getId());
					if (existingTask == null) {
						int id = taskManager.scheduleNewTask(task, engineId);
						task.setId(id);
						log.debug("[{}] New Task stored in DB: {}", task.getId(), task);
					} else {
						taskManager.updateTask(task);
						log.debug("[{}] Task updated in the pool: {}", task.getId(), task);
					}
				} catch (InternalErrorException e) {
					log.error("Error storing task {} into database: {}", task, e.getMessage());
				}
			}
		}
		addTask(task);
		dispatchersByTaskId.put(task.getId(), dispatcherQueue);
		log.debug("[{}] Task added to the pool: {}", task.getId(), task);
		return getSize();
	}

	@Override
	public Task removeTask(int id) throws TaskStoreException {
		return taskStore.removeTask(id);
	}

	@Override
	public DispatcherQueue getQueueForTask(Task task) throws InternalErrorException {
		if (task == null) {
			log.error("Supplied Task is null.");
			throw new IllegalArgumentException("Task cannot be null");
		}
		DispatcherQueue entry = dispatchersByTaskId.get(task.getId());
		if (entry == null) {
			throw new InternalErrorException("No Task with ID " + task.getId());
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
	public String getReport() {
		int waiting = getTasksWithStatus(TaskStatus.WAITING).size();
		int planned = getTasksWithStatus(TaskStatus.PLANNED).size();
		int generating = getTasksWithStatus(TaskStatus.GENERATING).size();
		int generated = getTasksWithStatus(TaskStatus.GENERATED).size();
		int generror = getTasksWithStatus(TaskStatus.GENERROR).size();
		int sending = getTasksWithStatus(TaskStatus.SENDING).size();
		int senderror = getTasksWithStatus(TaskStatus.SENDERROR).size();
		int done = getTasksWithStatus(TaskStatus.DONE).size();
		int error = getTasksWithStatus(TaskStatus.ERROR).size();

		return "Dispatcher SchedulingPool Task report:\n" +
				"  WAITING: " + waiting +
				"  PLANNED: " + planned +
				"  GENERATING: " + generating +
				"  GENERATED: " + generated +
				"  GENERROR: " + generror +
				"  SENDING:  " + sending +
				"  SENDEEROR:  " + senderror +
				"  DONE: " + done +
				"  ERROR: " + error;
	}

	@Override
	public void clear() {
		taskStore.clear();
		dispatchersByTaskId.clear();
		waitingTasksQueue.clear();
		waitingForcedTasksQueue.clear();
	}

	@Override
	public void reloadTasks() {

		log.debug("Going to reload Tasks from database...");

		this.clear();

		for (Pair<Task, Integer> pair : taskManager.listAllTasksAndClients()) {
			Task task = pair.getLeft();
			DispatcherQueue queue = dispatcherQueuePool.getDispatcherQueueByClient(pair.getRight());
			try {
				// just add DB Task to in-memory structure
				addToPool(task, queue);
			} catch (InternalErrorException | TaskStoreException e) {
				log.error("Adding Task {} and Queue {} into SchedulingPool failed, so the Task will be lost.", task, queue);
			}

			// if service was not in DONE or any kind of ERROR - reschedule now
			// error/done tasks will be rescheduled later by periodic jobs !!
			if (!Arrays.asList(TaskStatus.DONE, TaskStatus.ERROR, TaskStatus.GENERROR, TaskStatus.SENDERROR).contains(task.getStatus())) {
				scheduleTask(task, 0);
			}

		}

		log.debug("Reload of Tasks from database finished.");

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
		taskManager.updateTaskEngine(task, queueId);
	}

}
