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
import cz.metacentrum.perun.dispatcher.scheduling.TaskScheduler;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.model.ExecService;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.model.Task.TaskStatus;
import cz.metacentrum.perun.taskslib.model.TaskResult;
import cz.metacentrum.perun.taskslib.service.ResultManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

@org.springframework.stereotype.Service(value = "propagationMaintainer")
public class PropagationMaintainerImpl implements PropagationMaintainer {
	private final static Logger log = LoggerFactory
			.getLogger(PropagationMaintainerImpl.class);

	@Autowired
	private SchedulingPool schedulingPool;
	@Autowired
	private TaskScheduler taskScheduler;
	@Autowired
	private Perun perun;
	@Autowired
	private ResultManager resultManager;
	@Autowired
	private Properties dispatcherPropertiesBean;
	private PerunSession perunSession;
	private SimpleDateFormat dateFormat = new SimpleDateFormat();


	@Override
	public void checkResults() {

		try {
			perunSession = perun
					.getPerunSession(new PerunPrincipal(
									dispatcherPropertiesBean.getProperty("perun.principal.name"),
									dispatcherPropertiesBean
											.getProperty("perun.principal.extSourceName"),
									dispatcherPropertiesBean
											.getProperty("perun.principal.extSourceType")),
							new PerunClient());
		} catch (InternalErrorException e1) {
			log.error("Error establishing perun session to check tasks propagation status: ", e1);
			return;
		}

		rescheduleErrorTasks();

		endStuckTasks();

		rescheduleOldDoneTasks();

	}

	private void rescheduleErrorTasks() {
		log.info("Rescheduling necessary Tasks in ERROR state.");

		for (Task task : schedulingPool.getTasksWithStatus(TaskStatus.ERROR)) {
			if (task.getEndTime() == null) {
				log.error("RECOVERY FROM INCONSISTENT STATE: ERROR task does not have end_time! " +
						"Setting end_time to task.getDelay + 1.");
				// getDelay is in minutes, therefore we multiply it with 60*1000
				Date endTime = new Date(System.currentTimeMillis()
						- ((task.getDelay() + 1) * 60000));
				task.setEndTime(endTime);
			}
			int howManyMinutesAgo = (int) (System.currentTimeMillis() - task
					.getEndTime().getTime()) / 1000 / 60;
			if (howManyMinutesAgo < 0) {
				log.error("RECOVERY FROM INCONSISTENT STATE: ERROR task appears to have ended in future.");
				Date endTime = new Date(System.currentTimeMillis()
						- ((task.getDelay() + 1) * 60000));
				task.setEndTime(endTime);
				howManyMinutesAgo = task.getDelay() + 1;
			}
			log.info("TASK [{}] in ERROR state completed {} minutes ago.", task, howManyMinutesAgo);
			// If DELAY time has passed, we reschedule...
			int recurrence = task.getRecurrence() + 1;
			if (recurrence > task.getExecService().getDefaultRecurrence() &&
					howManyMinutesAgo < 60 * 12 &&
					!task.isSourceUpdated()) {
				log.info("TASK [{}] in ERROR state has no more retries, bailing out.", task);
			} else if (howManyMinutesAgo >= recurrence * task.getDelay() ||
					task.isSourceUpdated()) {
				// check if service is still assigned on facility
				boolean removeTask = false;
				try {
					List<Service> assignedServices = perun.getServicesManager().getAssignedServices(perunSession, task.getFacility());
					if (assignedServices.contains(task.getExecService().getService())) {
						ExecService execService = task.getExecService();
						Facility facility = task.getFacility();
						if (recurrence > execService.getDefaultRecurrence()) {
							// this ERROR task is rescheduled for being here too long
							task.setRecurrence(0);
							task.setDestinations(null);
							log.info("TASK id {} is in ERROR state long enough.", task.getId());
						}
						task.setRecurrence(recurrence);
						log.info("TASK [{}] in ERROR state is going to be rescheduled with ExecService id: {} " +
								"on Facility id: {}", new Object[]{task, execService.getId(), facility.getId()});
						schedulingPool.addTaskSchedule(task, -1);

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
		List<Task> suspiciousTasks = schedulingPool.getTasksWithStatus(TaskStatus.GENERATING, TaskStatus.SENDING);

		for (Task task : suspiciousTasks) {
			// count how many minutes the task stays in one state
			Date genStarted = task.getGenStartTime();
			Date sendStarted = task.getSendStartTime();

			if (genStarted == null && sendStarted == null) {
				log.error("ERROR: Task presumably in PLANNED or PROCESSING state, but does not have a valid scheduled " +
						"or started time. Switching to ERROR. {}", task);
				task.setEndTime(new Date(System.currentTimeMillis()));
				task.setStatus(TaskStatus.ERROR);
				continue;
			}

			int howManyMinutesAgo = (int) (System.currentTimeMillis() - (sendStarted == null ? genStarted
					: sendStarted).getTime()) / 1000 / 60;

			// If too much time has passed something is broken
			if (howManyMinutesAgo >= 60) {
				log.error("ERROR: Task is stuck in PLANNED or PROCESSING state. Switching it to ERROR. {}", task);
				task.setEndTime(new Date(System.currentTimeMillis()));
				task.setStatus(TaskStatus.ERROR);
			}

		}
	}

	private void rescheduleOldDoneTasks() {
		// Reschedule SEND tasks in DONE that haven't been running for quite a while
		log.info("I am gonna list complete tasks and reschedule if they are too old.");

		for (Task task : schedulingPool.getTasksWithStatus(TaskStatus.DONE)) {

			Date twoDaysAgo = new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 2);
			if (task.isSourceUpdated()) {
				// reschedule the task
				log.info("TASK [{}] data changed. Going to schedule for propagation now.", task);
				schedulingPool.addTaskSchedule(task, -1);
			} else if (task.getEndTime() == null || task.getEndTime().before(twoDaysAgo)) {
				// reschedule the task
				log.info("TASK [{}] wasn't propagated for more then 2 days. Going to schedule it for propagation now.",
						task);
				schedulingPool.addTaskSchedule(task, -1);
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
		log.debug("Switching PROCESSING tasks on engine {} to ERROR, the engine went down", clientID);
		for (Task task : tasks) {
			if (engineStates.contains(task.getStatus())) {
				log.debug("switching task {} to ERROR, the engine it was running on went down", task.getId());
				task.setStatus(TaskStatus.ERROR);
			}
			try {
				schedulingPool.setQueueForTask(task, null);
			} catch (InternalErrorException e) {
				log.error("Could not remove output queue for task {}: {}", task.getId(), e.getMessage());
			}
		}
	}

	@Override
	public void onTaskStatusChange(int taskId, String status, String date) {
		log.debug("TESTSTR --> onTaskSatusChange ran with taskId {} status {} date {}",
				new Object[]{taskId, status, date});
		log.debug("Changing state of Task with id 1{} to {} as reported by Engine", taskId, status);
		Task task = schedulingPool.getTask(taskId);
		if (task == null) {
			log.error("Received status update about Task with id {} that is not in Dispatcher, will ignore it", taskId);
			return;
		}
		log.debug("TESTSTR --> Found task {} for if {}", task, taskId);
		TaskStatus oldStatus = task.getStatus();
		task.setStatus(TaskStatus.valueOf(status));
		Date changeDate;
		try {
			changeDate = dateFormat.parse(date);
		} catch (ParseException e) {
			log.warn("EndDate {} of {} could not be parsed, current time will be used instead.", date, task);
			changeDate = new Date(System.currentTimeMillis());
		}

		switch (task.getStatus()) {
			case WAITING:
			case PLANNED:
				log.error("Received {} {} from Engine, this should not happen.", task.getStatus(), task);
				return;
			case GENERATING:
				task.setGenStartTime(changeDate);
				break;
			case GENERROR:
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
		log.info("{} changed state from {} to {}", new Object[]{task, oldStatus, task.getStatus()});
	}

	@Override
	public void onTaskDestinationComplete(int clientID, String string) {
		if (string == null || string.isEmpty()) {
			log.error("Could not parse taskresult message from engine " + clientID);
			return;
		}

		try {
			List<PerunBean> listOfBeans = AuditParser.parseLog(string);
			if (!listOfBeans.isEmpty()) {
				TaskResult taskResult = (TaskResult) listOfBeans.get(0);
				resultManager.insertNewTaskResult(taskResult, clientID);
			} else {
				log.error("No TaskResult bean found in message {} from engine {}", string, clientID);
			}
		} catch (Exception e) {
			log.error("Could not save taskresult message {} from engine " + clientID, string);
			log.debug("Error storing taskresult message: " + e.getMessage());
		}
	}

	public TaskScheduler getTaskScheduler() {
		return taskScheduler;
	}

	public void setTaskScheduler(TaskScheduler taskScheduler) {
		this.taskScheduler = taskScheduler;
	}

	public Properties getDispatcherPropertiesBean() {
		return dispatcherPropertiesBean;
	}

	public void setDispatcherPropertiesBean(Properties propertiesBean) {
		this.dispatcherPropertiesBean = propertiesBean;
	}

}
