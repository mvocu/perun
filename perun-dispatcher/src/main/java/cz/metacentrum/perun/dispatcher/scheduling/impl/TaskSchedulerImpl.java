package cz.metacentrum.perun.dispatcher.scheduling.impl;

import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Perun;
import cz.metacentrum.perun.core.api.PerunClient;
import cz.metacentrum.perun.core.api.PerunPrincipal;
import cz.metacentrum.perun.core.api.PerunSession;
import cz.metacentrum.perun.core.api.exceptions.FacilityNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PrivilegeException;
import cz.metacentrum.perun.core.api.exceptions.ServiceNotExistsException;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueue;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueuePool;
import cz.metacentrum.perun.dispatcher.scheduling.DenialsResolver;
import cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool;
import cz.metacentrum.perun.dispatcher.scheduling.TaskScheduler;
import cz.metacentrum.perun.taskslib.model.ExecService;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.model.Task.TaskStatus;
import cz.metacentrum.perun.taskslib.model.TaskSchedule;
import cz.metacentrum.perun.taskslib.runners.impl.AbstractRunner;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

import static cz.metacentrum.perun.dispatcher.scheduling.impl.TaskScheduled.*;


@org.springframework.stereotype.Service(value = "taskScheduler")
public class TaskSchedulerImpl extends AbstractRunner implements TaskScheduler {
	private final static Logger log = LoggerFactory
			.getLogger(TaskSchedulerImpl.class);

	@Autowired
	private SchedulingPool schedulingPool;
	@Autowired
	private Perun perun;
	private PerunSession perunSession;
	@Autowired
	private Properties dispatcherPropertiesBean;
	@Autowired
	private DispatcherQueuePool dispatcherQueuePool;
	@Autowired
	private DenialsResolver denialsResolver;
	@Autowired
	private DelayQueue<TaskSchedule> waitingTasksQueue;
	@Autowired
	private DelayQueue<TaskSchedule> waitingForcedTasksQueue;

	/**
	 * This method runs in separate thread perpetually trying to take tasks from delay queue, blocking if none are available.
	 * If there is Task ready, we check if it source was updated. If it was, we put the task back to the queue (This
	 * can happen only limited number of times). If on the other hand it was not updated we perform additional checks using
	 * method scheduleTask.
	 */
	@Override
	public void run() {
		try {
			initPerunSession();
		} catch (InternalErrorException e1) {
			String message = "Dispatcher was unable to initialize Perun session.";
			log.error(message, e1);
			throw new RuntimeException(message, e1);
		}
		log.debug("pool contains {} tasks in total", schedulingPool.getSize());
		TaskSchedule schedule;
		while (!shouldStop()) {
			try {
				schedule = getTaskSchedule();
			} catch (InterruptedException e) {
				String message = "Thread was interrupted, cannot continue.";
				log.error(message, e);
				throw new RuntimeException(message, e);
			}
			Task task = schedule.getTask();
			if (task.isSourceUpdated() && schedule.getDelayCount() > 0) {
				schedulingPool.addTaskSchedule(task, schedule.getDelayCount() - 1, true);
				log.info("Task {} was not allowed to be sent to Engine now.", task);
			} else {
				TaskScheduled reason = scheduleTask(task);
				switch (reason) {
					case QUEUE_ERROR:
						log.warn("Task {} dispatcherQueue could not be set, so it is rescheduled.", task);
						schedulingPool.addTaskSchedule(task, -1);
						break;
					case DENIED:
						log.info("Execution was denied fo Task {}.", task);
						//#TODO: Figure out
						break;
					case ERROR:
						log.error("Unexpected error occurred while scheduling Task {} for sending to Engine.", task);
						schedulingPool.addTaskSchedule(task, -1);
						break;
					case SUCCESS:
						log.info("Task {} was successfully queued for sending to Engine.", task);
						break;
					case DB_ERROR:
						log.warn("Facility and ExecService could not be found in DB for Task {}.", task);
						//#TODO: Figure out
						break;
				}
			}
		}
	}

	/**
	 * Internal method which chooses next Task that will be processed, we try to take forced Task first,
	 * and if none is available, then we wait for a normal Task for a few seconds.
	 *
	 * @return Once one of the Queues returns non null TaskSchedule, we return it.
	 * @throws InterruptedException
	 */
	private TaskSchedule getTaskSchedule() throws InterruptedException {
		TaskSchedule taskSchedule = null;
		while (!shouldStop()) {
			log.debug("Trying to get a Task to send to Dispatcher.");
			log.debug("SchedulingPool has {} Tasks", schedulingPool.getSize());
			log.debug("WaitingTasksQueue has {} Tasks", waitingTasksQueue.size());
			taskSchedule = waitingForcedTasksQueue.poll();
			if (taskSchedule == null) {
				taskSchedule = waitingTasksQueue.poll(10, TimeUnit.SECONDS);
			}
			if (taskSchedule != null) {
				break;
			}
		}
		log.debug("Returning task schedule {} \n\n\n", taskSchedule);
		return taskSchedule;
	}

	@Override
	public TaskScheduled scheduleTask(Task task) {
		ExecService execService = task.getExecService();
		Facility facility = task.getFacility();
		Date time = new Date(System.currentTimeMillis());
		DispatcherQueue dispatcherQueue = null;

		log.debug("Scheduling Task {}.", task.toString());
		try {
			dispatcherQueue = schedulingPool.getQueueForTask(task);
			log.debug("Task {} is assigned to queue {}", task.getId(),
					(dispatcherQueue == null) ? "null" : dispatcherQueue.getClientID());
		} catch (InternalErrorException e) {
			log.warn("Task {} is not assigned to any queue", task.getId());
		}
		// check if the engine is still registered
		if (dispatcherQueue != null &&
				!dispatcherQueuePool.isThereDispatcherQueueForClient(dispatcherQueue.getClientID())) {
			dispatcherQueue = null;
		}
		if (dispatcherQueue == null) {
			// where should we send the task?
			dispatcherQueue = dispatcherQueuePool.getAvailableQueue();
			if (dispatcherQueue != null) {
				try {
					schedulingPool.setQueueForTask(task, dispatcherQueue);
				} catch (InternalErrorException e) {
					log.error("Could not set client queue for task {}: {}", task.getId(), e.getMessage());
					return QUEUE_ERROR;
				}
				log.debug("Assigned new queue {} to task {}", dispatcherQueue.getQueueName(), task.getId());
			} else {
				log.error("Task {} has no engine assigned and there are no engines registered.", task.toString());
				return QUEUE_ERROR;
			}
		}

		log.debug("Facility to be processed: {}, ExecService to be processed: {}",
				facility.getId(), execService.getId());
		log.debug("Is the execService ID: {} enabled globally?", execService.getId());
		if (execService.isEnabled()) {
			log.debug("   Yes, it is globally enabled.");
		} else {
			log.debug("   No, execService ID: {} is not enabled globally. Task will not run.", execService.getId());
			return DENIED;
		}

		log.debug("   Is the execService ID: {} denied on facility ID: {}?",
				execService.getId(), facility.getId());
		try {
			if (!denialsResolver.isExecServiceDeniedOnFacility(execService, facility)) {
				log.debug("   No, it is not.");
			} else {
				log.debug("   Yes, the execService ID: {} is denied on facility ID: {}. Task will not run.",
						execService.getId(), facility.getId());
				return DENIED;
			}
		} catch (InternalErrorException e) {
			log.error("Error getting disabled status for execService, task will not run now.");
			return ERROR;
		}
		return sendToEngine(task);
	}

	private TaskScheduled sendToEngine(Task task) {
		DispatcherQueue dispatcherQueue;
		try {
			dispatcherQueue = schedulingPool.getQueueForTask(task);
		} catch (InternalErrorException e1) {
			log.error("No engine set for task {}, could not send it!", task.toString());
			return ERROR;
		}

		if (dispatcherQueue == null) {
			// where should we send the task?
			if (dispatcherQueuePool.poolSize() > 0) {
				dispatcherQueue = dispatcherQueuePool.getPool().iterator()
						.next();
				try {
					schedulingPool.setQueueForTask(task, dispatcherQueue);
				} catch (InternalErrorException e) {
					log.error("Could not assign new queue for task {}: {}", task.getId(), e);
					return QUEUE_ERROR;
				}
				log.debug("Assigned new queue {} to task {}",
						dispatcherQueue.getQueueName(), task.getId());
			} else {
				log.error("Task {} has no engine assigned and there are no engines registered.", task.toString());
				return QUEUE_ERROR;
			}
		}

		// task|[engine_id]|[task_id][is_forced][exec_service_id][facility]|[destination_list]|[dependency_list]
		// - the task|[engine_id] part is added by dispatcherQueue
		List<Destination> destinations = task.getDestinations();
		if (task.isSourceUpdated() || destinations == null || destinations.isEmpty()) {
			log.debug("No destinations for task {}, trying to query the database.", task.toString());
			try {
				initPerunSession();
				destinations = perun.getServicesManager().getDestinations(
						perunSession, task.getExecService().getService(),
						task.getFacility());
			} catch (ServiceNotExistsException e) {
				log.error("No destinations found for task {}", task.getId());
				task.setEndTime(new Date(System.currentTimeMillis()));
				task.setStatus(TaskStatus.ERROR);
				return DB_ERROR;
			} catch (FacilityNotExistsException e) {
				log.error("Facility for task {} does not exist...", task.getId());
				task.setEndTime(new Date(System.currentTimeMillis()));
				task.setStatus(TaskStatus.ERROR);
				return DB_ERROR;
			} catch (PrivilegeException e) {
				log.error("Privilege error accessing the database: {}", e.getMessage());
				task.setEndTime(new Date(System.currentTimeMillis()));
				task.setStatus(TaskStatus.ERROR);
				return DB_ERROR;
			} catch (InternalErrorException e) {
				log.error("Internal error: {}", e.getMessage());
				task.setEndTime(new Date(System.currentTimeMillis()));
				task.setStatus(TaskStatus.ERROR);
				return DB_ERROR;
			}
		}
		log.debug("Fetched destinations: " + ((destinations == null) ? "[]" : destinations.toString()));
		task.setDestinations(destinations);
		StringBuilder destinations_s = new StringBuilder("Destinations [");
		if (destinations != null) {
			for (Destination destination : destinations) {
				destinations_s.append(destination.serializeToString()).append(", ");
			}
		}
		destinations_s.append("]");
		String dependencies = "";
		dispatcherQueue.sendMessage("[" + task.getId() + "]["
				+ task.isPropagationForced() + "]|["
				+ fixStringSeparators(task.getExecService().serializeToString()) + "]|["
				+ fixStringSeparators(task.getFacility().serializeToString()) + "]|["
				+ fixStringSeparators(destinations_s.toString()) + "]|[" + dependencies + "]");
		task.setStartTime(new Date(System.currentTimeMillis()));
		task.setEndTime(null);
		return SUCCESS;
	}

	private String fixStringSeparators(String data) {
		if (data.contains("|")) {
			return new String(Base64.encodeBase64(data.getBytes()));
		} else {
			return data;
		}
	}

	public SchedulingPool getSchedulingPool() {
		return schedulingPool;
	}

	public void setSchedulingPool(SchedulingPool schedulingPool) {
		this.schedulingPool = schedulingPool;
	}

	protected void initPerunSession() throws InternalErrorException {
		if (perunSession == null) {
			perunSession = perun
					.getPerunSession(new PerunPrincipal(
							dispatcherPropertiesBean.getProperty("perun.principal.name"),
							dispatcherPropertiesBean
									.getProperty("perun.principal.extSourceName"),
							dispatcherPropertiesBean
									.getProperty("perun.principal.extSourceType")),
							new PerunClient());
		}
	}
}
