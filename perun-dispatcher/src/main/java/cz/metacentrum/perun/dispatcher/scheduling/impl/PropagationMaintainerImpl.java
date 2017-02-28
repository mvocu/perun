package cz.metacentrum.perun.dispatcher.scheduling.impl;

import cz.metacentrum.perun.auditparser.AuditParser;
import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Perun;
import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.core.api.PerunClient;
import cz.metacentrum.perun.core.api.PerunPrincipal;
import cz.metacentrum.perun.core.api.PerunSession;
import cz.metacentrum.perun.core.api.Service;
import cz.metacentrum.perun.core.api.exceptions.FacilityNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PrivilegeException;
import cz.metacentrum.perun.dispatcher.scheduling.PropagationMaintainer;
import cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool;
import cz.metacentrum.perun.taskslib.dao.ServiceDenialDao;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.model.Task.TaskStatus;
import cz.metacentrum.perun.taskslib.model.TaskResult;
import cz.metacentrum.perun.taskslib.service.ResultManager;
import cz.metacentrum.perun.taskslib.service.TaskManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@org.springframework.stereotype.Service(value = "propagationMaintainer")
public class PropagationMaintainerImpl implements PropagationMaintainer {

	private final static Logger log = LoggerFactory.getLogger(PropagationMaintainerImpl.class);
	private final static int rescheduleTime = 190;

	private PerunSession perunSession;

	private Perun perun;
	private SchedulingPool schedulingPool;
	private ResultManager resultManager;
	private TaskManager taskManager;
	private ServiceDenialDao serviceDenialDao;
	private Properties dispatcherProperties;

	// ----- setters -------------------------------------

	public SchedulingPool getSchedulingPool() {
		return schedulingPool;
	}

	@Autowired
	public void setSchedulingPool(SchedulingPool schedulingPool) {
		this.schedulingPool = schedulingPool;
	}

	public Perun getPerun() {
		return perun;
	}

	@Autowired
	public void setPerun(Perun perun) {
		this.perun = perun;
	}

	public ResultManager getResultManager() {
		return resultManager;
	}

	@Autowired
	public void setResultManager(ResultManager resultManager) {
		this.resultManager = resultManager;
	}

	public Properties getDispatcherProperties() {
		return dispatcherProperties;
	}

	@Autowired
	public void setDispatcherProperties(Properties dispatcherProperties) {
		this.dispatcherProperties = dispatcherProperties;
	}

	public TaskManager getTaskManager() {
		return taskManager;
	}

	@Autowired
	public void setTaskManager(TaskManager taskManager) {
		this.taskManager = taskManager;
	}

	public ServiceDenialDao getServiceDenialDao() {
		return serviceDenialDao;
	}

	@Autowired
	public void setServiceDenialDao(ServiceDenialDao serviceDenialDao) {
		this.serviceDenialDao = serviceDenialDao;
	}


	// ----- methods -------------------------------------


	@Override
	public void checkResults() {

		try {
			if (perunSession == null) {
				perunSession = perun.getPerunSession(new PerunPrincipal(
								dispatcherProperties.getProperty("perun.principal.name"),
								dispatcherProperties.getProperty("perun.principal.extSourceName"),
								dispatcherProperties.getProperty("perun.principal.extSourceType")),
						new PerunClient());
			}
		} catch (InternalErrorException e1) {
			log.error("Error establishing perun session to check tasks propagation status: ", e1);
			return;
		}

		rescheduleErrorTasks();

		endStuckTasks();

		rescheduleOldDoneTasks();

	}

	/**
	 * Reschedule Tasks in Error if their
	 * - source was updated
	 * - OR recurrence is <= default recurrence (2) and ended time (minutes) >= delay*(recurrence+1)
	 */
	private void rescheduleErrorTasks() {

		log.info("Rescheduling necessary Tasks in ERROR state.");

		for (Task task : schedulingPool.getTasksWithStatus(TaskStatus.ERROR, TaskStatus.GENERROR, TaskStatus.SENDERROR)) {

			// error tasks should have correct end time
			if (task.getEndTime() == null) {
				log.error("RECOVERY FROM INCONSISTENT STATE: ERROR task does not have end_time! " +
						"Setting end_time to task.getDelay + 1.");
				// getDelay is in minutes, therefore we multiply it with 60*1000
				Date endTime = new Date(System.currentTimeMillis() - ((task.getDelay() + 1) * 60000));
				task.setEndTime(endTime);
			}

			int howManyMinutesAgo = (int) (System.currentTimeMillis() - task.getEndTime().getTime()) / 1000 / 60;

			if (howManyMinutesAgo < 0) {
				log.error("RECOVERY FROM INCONSISTENT STATE: ERROR task appears to have ended in future.");
				Date endTime = new Date(System.currentTimeMillis() - ((task.getDelay() + 1) * 60000));
				task.setEndTime(endTime);
				howManyMinutesAgo = task.getDelay() + 1;
			}
			log.info("TASK [{}] in ERROR state completed {} minutes ago.", task, howManyMinutesAgo);

			// If DELAY time has passed, we reschedule...
			int recurrence = task.getRecurrence() + 1;

			if (recurrence > task.getService().getRecurrence() && howManyMinutesAgo < 60 * 12 && !task.isSourceUpdated()) {
				// exceeded own recurrence, ended in less than 12 hours ago and source was not updated
				// FIXME - is time condition really necessary ?
				log.info("TASK [{}] in ERROR state has no more retries, bailing out.", task);
			} else if (howManyMinutesAgo >= recurrence * task.getDelay() || task.isSourceUpdated()) {
				// within recurrence, ended more than (recurrence*delay) ago, or source is updated
				// check if service is still assigned on facility
				boolean removeTask = false;
				try {
					List<Service> assignedServices = perun.getServicesManager().getAssignedServices(perunSession, task.getFacility());
					if (assignedServices.contains(task.getService())) {
						Service service = task.getService();
						Facility facility = task.getFacility();
						task.setRecurrence(recurrence);
						if (recurrence > service.getRecurrence()) {
							// this ERROR task is rescheduled for being here too long
							task.setRecurrence(0);
							task.setDestinations(null);
							log.info("TASK id {} is in ERROR state long enough.", task.getId());
						}
						task.setSourceUpdated(false);
						if (!serviceDenialDao.isServiceBlockedOnFacility(service.getId(), facility.getId()) && service.isEnabled()) {
							log.info("TASK [{}] in ERROR state is going to be rescheduled with Service id: {} " +
									"on Facility id: {}", new Object[]{task, service.getId(), facility.getId()});
							schedulingPool.scheduleTask(task, -1);
						} else {
							log.info("TASK [{}] in ERROR state is NOT going to be rescheduled with Service id: {} " +
									"on Facility id: {}, because its blocked on facility or globally.", new Object[]{task, service.getId(), facility.getId()});
						}
					} else {
						removeTask = true;
						log.warn("Will remove TASK {} from database, because service is no longer assigned " +
								"to this facility.", task.toString());
					}
				} catch (FacilityNotExistsException e) {
					removeTask = true;
					log.error("Removed TASK {} from database, facility no longer exists.", task.getId());
				} catch (InternalErrorException e) {
					log.error("{}", e);
				} catch (PrivilegeException e) {
					log.error("Consistency error. {}", e);
				}
				if (removeTask) {
					try {
						schedulingPool.removeTask(task);
					} catch (TaskStoreException e) {
						log.error("Could not remove invalid Task {} from schedulingPool", task, e);
					}
				}
			}
		}
	}

	private void endStuckTasks() {
		// list all tasks in processing and planned and check if any have been running for too long.
		log.info("Ending Tasks stuck in Engine for too long.");
		List<Task> suspiciousTasks = schedulingPool.getTasksWithStatus(TaskStatus.GENERATING, TaskStatus.SENDING, TaskStatus.GENERATED);

		for (Task task : suspiciousTasks) {
			// count how many minutes the task stays in one state
			Date genStarted = task.getGenStartTime();
			Date sendStarted = task.getSendStartTime();

			if (genStarted == null && sendStarted == null) {
				log.error("ERROR: Task presumably in GENERATING or SENDING state, but does not have a valid genStartTime" +
						" or sendStartTime. Switching to ERROR. {}", task);
				task.setEndTime(new Date(System.currentTimeMillis()));
				task.setStatus(TaskStatus.ERROR);
				continue;
			}

			int howManyMinutesAgo = (int) (System.currentTimeMillis() - (sendStarted == null ? genStarted : sendStarted).getTime()) / 1000 / 60;

			// If too much time has passed something is broken
			if (howManyMinutesAgo >= rescheduleTime) {
				log.error("ERROR: Task is stuck in GENERATING/GENERATED or SENDING state for more than {} minutes. Switching it to ERROR. {}", rescheduleTime, task);
				task.setEndTime(new Date(System.currentTimeMillis()));
				task.setStatus(TaskStatus.ERROR);
			}

		}
	}

	private void rescheduleOldDoneTasks() {
		// Reschedule SEND tasks in DONE that haven't been running for quite a while
		log.info("I am gonna list completed tasks and reschedule if they are too old or source was updated.");

		for (Task task : schedulingPool.getTasksWithStatus(TaskStatus.DONE)) {

			Date twoDaysAgo = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 2);
			if (task.isSourceUpdated()) {
				// reschedule the task if not blocked
				if (!serviceDenialDao.isServiceBlockedOnFacility(task.getService().getId(), task.getFacility().getId())) {
					List<Service> assignedServices = null;
					try {
						assignedServices = perun.getServicesManager().getAssignedServices(perunSession, task.getFacility());
					} catch (InternalErrorException e) {
						e.printStackTrace();
					} catch (FacilityNotExistsException e) {
						e.printStackTrace();
					} catch (PrivilegeException e) {
						e.printStackTrace();
					}
					if (assignedServices.contains(task.getService())) {

					}
					log.info("TASK [{}] data changed. Going to schedule for propagation now.", task);
					// TODO - if forced task, set delay count to 0
					schedulingPool.scheduleTask(task, -1);
					task.setSourceUpdated(false);
				}
			} else if (task.getEndTime() == null || task.getEndTime().before(twoDaysAgo)) {
				// reschedule the task
				log.info("TASK [{}] wasn't propagated for more then 2 days. Going to schedule it for propagation now.", task);
				schedulingPool.scheduleTask(task, -1);
			} else {
				log.info("TASK [{}] has finished recently, leaving it for now.", task);
			}
		}
	}

	@Override
	public void closeTasksForEngine(int clientID) {

		List<Task> tasks = schedulingPool.getTasksForEngine(clientID);
		List<TaskStatus> engineStates = new ArrayList<>();
		engineStates.add(TaskStatus.PLANNED);
		engineStates.add(TaskStatus.GENERATING);
		engineStates.add(TaskStatus.GENERATED);
		engineStates.add(TaskStatus.SENDING);

		// switch all processing tasks to error, remove the engine queue association
		log.debug("Switching processing tasks on engine {} to ERROR, the engine went down...", clientID);
		for (Task task : tasks) {
			if (engineStates.contains(task.getStatus())) {
				log.debug("[{}] Switching Task to ERROR, the engine it was running on went down.", task.getId());
				task.setStatus(TaskStatus.ERROR);
			}
			try {
				schedulingPool.setQueueForTask(task, null);
			} catch (InternalErrorException e) {
				log.error("[{}] Could not remove dispatcher queue for task: {}.", task.getId(), e.getMessage());
			}
		}

	}

	@Override
	public void onTaskStatusChange(int taskId, String status, String milliseconds) {

		Task task = schedulingPool.getTask(taskId);
		if (task == null) {
			log.error("[{}] Received status update about Task which is not in Dispatcher anymore, will ignore it.", taskId);
			return;
		}

		TaskStatus oldStatus = task.getStatus();
		task.setStatus(TaskStatus.valueOf(status));
		long ms;
		try {
			ms = Long.valueOf(milliseconds);
		} catch (NumberFormatException e) {
			log.warn("[{}] Timestamp of change '{}' could not be parsed, current time will be used instead.", task.getId(), milliseconds);
			ms = System.currentTimeMillis();
		}
		Date changeDate = new Date(ms);

		// FIXME - based on state we should reset "forced" flag (but first we must handle it correctly from auditer events
		// FIXME - and check how engine handles it).

		switch (task.getStatus()) {
			case WAITING:
			case PLANNED:
				log.error("[{}] Received status change to {} from Engine, this should not happen.", task.getId(), task.getStatus());
				return;
			case GENERATING:
				task.setStartTime(changeDate);
				task.setGenStartTime(changeDate);
				break;
			case GENERROR:
				task.setEndTime(changeDate);
			case GENERATED:
				task.setGenEndTime(changeDate);
				break;
			case SENDING:
				task.setSendStartTime(changeDate);
				break;
			case DONE:
			case SENDERROR:
				task.setSendEndTime(changeDate);
				task.setEndTime(changeDate);
				break;
			case ERROR:
				task.setEndTime(changeDate);
				break;
		}

		taskManager.updateTask(task);

		log.debug("[{}] Task status changed from {} to {} as reported by Engine: {}.", new Object[]{task.getId(), oldStatus, task.getStatus(), task});

	}

	@Override
	public void onTaskDestinationComplete(int clientID, String string) {

		if (string == null || string.isEmpty()) {
			log.error("Could not parse TaskResult message from Engine " + clientID + ".");
			return;
		}

		try {
			List<PerunBean> listOfBeans = AuditParser.parseLog(string);
			if (!listOfBeans.isEmpty()) {
				TaskResult taskResult = (TaskResult) listOfBeans.get(0);
				log.debug("[{}] Received TaskResult for Task from Engine {}.", taskResult.getTaskId(), clientID);
				resultManager.insertNewTaskResult(taskResult, clientID);
			} else {
				log.error("No TaskResult found in message from Engine {}: {}.", clientID, string);
			}
		} catch (Exception e) {
			log.error("Could not save TaskResult from Engine " + clientID + " {}, {}", string, e.getMessage());
		}

	}

}
