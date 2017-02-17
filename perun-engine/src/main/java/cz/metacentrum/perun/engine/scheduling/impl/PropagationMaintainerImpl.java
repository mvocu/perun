package cz.metacentrum.perun.engine.scheduling.impl;

import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.engine.jms.JMSQueueManager;
import cz.metacentrum.perun.engine.scheduling.PropagationMaintainer;
import cz.metacentrum.perun.engine.scheduling.SchedulingPool;
import cz.metacentrum.perun.taskslib.model.SendTask;
import cz.metacentrum.perun.taskslib.model.SendTask.SendTaskStatus;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.model.Task.TaskStatus;
import cz.metacentrum.perun.taskslib.model.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jms.JMSException;
import java.util.concurrent.Future;

@org.springframework.stereotype.Service(value = "propagationMaintainer")
public class PropagationMaintainerImpl implements PropagationMaintainer {

	private final static Logger log = LoggerFactory.getLogger(PropagationMaintainerImpl.class);

	/**
	 * After how many minutes is processing Task considered as stuck and re-scheduled.
	 */
	private final static int rescheduleTime = 180;

	private SchedulingPool schedulingPool;
	private JMSQueueManager jmsQueueManager;

	// ----- setters ------------------------------

	public JMSQueueManager getJmsQueueManager() {
		return jmsQueueManager;
	}

	@Autowired
	public void setJmsQueueManager(JMSQueueManager jmsQueueManager) {
		this.jmsQueueManager = jmsQueueManager;
	}

	public SchedulingPool getSchedulingPool() {
		return schedulingPool;
	}

	@Autowired
	public void setSchedulingPool(SchedulingPool schedulingPool) {
		this.schedulingPool = schedulingPool;
	}

	// ----- methods ------------------------------

	public void endStuckTasks() {

		// handle stuck GEN tasks
		for (Task task : schedulingPool.getGeneratingTasksBlockingMap().values()) {
			int howManyMinutesAgo = (int) (System.currentTimeMillis() - task.getStartTime().getTime()) / 1000 / 60;
			// If too much time has passed something is broken
			if (howManyMinutesAgo >= rescheduleTime) {
				task.setStatus(TaskStatus.GENERROR);
				Task removed = null;
				try {
					removed = schedulingPool.removeTask(task);
				} catch (TaskStoreException e) {
					log.error("Failed during removal of Task {} from SchedulingPool", task);
				}
				if (removed == null) {
					log.error("Stale Task {} was not removed.", task);
				}
				try {
					jmsQueueManager.reportTaskStatus(task.getId(), task.getStatus(), System.currentTimeMillis());
				} catch (JMSException e) {
					log.warn("Error trying to send {} to Dispatcher: {}", task, e);
				}
			}
		}

		// handle stuck SEND tasks
		for (SendTask sendTask : schedulingPool.getSendingSendTasksBlockingMap().values()) {
			int howManyMinutesAgo = (int) (System.currentTimeMillis() - sendTask.getStartTime().getTime()) / 1000 / 60;
			// If too much time has passed something is broken
			if (howManyMinutesAgo >= rescheduleTime) {
				sendTask.setStatus(SendTaskStatus.ERROR);
				Future<SendTask> sendTaskFuture = null;
				try {
					sendTaskFuture = schedulingPool.removeSendTaskFuture(
							sendTask.getId().getLeft(), sendTask.getId().getRight());
				} catch (TaskStoreException e) {
					log.error("Failed during removal of SendTaskFuture {} from SchedulingPool", sendTaskFuture);
				}
				if (sendTaskFuture == null) {
					log.error("Stale SendTask {} was not removed.", sendTask);
				}
				TaskResult taskResult = null;
				try {
					taskResult = schedulingPool.createTaskResult(sendTask.getId().getLeft(),
							sendTask.getDestination().getId(), sendTask.getStderr(), sendTask.getStdout(),
							sendTask.getReturnCode(), sendTask.getTask().getService());
					jmsQueueManager.reportTaskResult(taskResult);
				} catch (JMSException e) {
					log.warn("Error trying to send {} to Dispatcher: {}", taskResult, e);
				}

			}
		}
	}

}
