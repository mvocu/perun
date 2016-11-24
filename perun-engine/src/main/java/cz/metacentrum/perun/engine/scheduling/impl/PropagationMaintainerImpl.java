package cz.metacentrum.perun.engine.scheduling.impl;

import cz.metacentrum.perun.core.api.Perun;
import cz.metacentrum.perun.engine.jms.JMSQueueManager;
import cz.metacentrum.perun.engine.scheduling.PropagationMaintainer;
import cz.metacentrum.perun.engine.scheduling.SchedulingPool;
import cz.metacentrum.perun.taskslib.model.SendTask;
import cz.metacentrum.perun.taskslib.model.SendTask.SendTaskStatus;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.model.Task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jms.JMSException;
import java.util.Date;
import java.util.concurrent.Future;

@org.springframework.stereotype.Service(value = "propagationMaintainer")
public class PropagationMaintainerImpl implements PropagationMaintainer {
	private final static Logger log = LoggerFactory
			.getLogger(PropagationMaintainerImpl.class);

	@Autowired
	private SchedulingPool schedulingPool;
	@Autowired
	private JMSQueueManager jmsQueueManager;

	private void logJmsError(JMSException e, Object o) {
		log.warn("Error occured trying to send {} to Dispatcher", o, e);
	}

	public void endStuckTasks() {
		//TODO: where and how to get this
		long stuckTimeLimit = 10000L;
		long now = new Date(System.currentTimeMillis()).getTime();


		for (Task task : schedulingPool.getGeneratingTasksBlockingMap().values()) {
			if ((task.getStartTime().getTime() - now) > stuckTimeLimit) {
				task.setStatus(TaskStatus.GENERROR);
				Task removed = schedulingPool.removeTask(task);
				if (removed != null) {
					log.error("Stale Task {} was not removed.", task);
				}
				try {
					jmsQueueManager.reportGenTask(task);
				} catch (JMSException e) {
					logJmsError(e, task);
				}
			}
		}

		for (SendTask sendTask : schedulingPool.getSendingSendTasksBlockingMap().values()) {
			if ((sendTask.getStartTime().getTime() - now) > stuckTimeLimit) {
				sendTask.setStatus(SendTaskStatus.ERROR);
				Future<SendTask> sendTaskFuture = schedulingPool.removeSendTaskFuture(
						sendTask.getId().getLeft(), sendTask.getId().getRight());
				if (sendTaskFuture == null) {
					log.error("Stale SendTask {} was not removed.", sendTask);
				}
				try {
					jmsQueueManager.reportSendTask(sendTask);
				} catch (JMSException e) {
					logJmsError(e, sendTask);
				}

			}
		}
	}

	public JMSQueueManager getJmsQueueManager() {
		return jmsQueueManager;
	}

	public void setJmsQueueManager(JMSQueueManager jmsQueueManager) {
		this.jmsQueueManager = jmsQueueManager;
	}

}
