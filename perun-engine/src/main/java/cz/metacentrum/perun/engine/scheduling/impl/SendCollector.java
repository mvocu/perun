package cz.metacentrum.perun.engine.scheduling.impl;


import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.engine.exceptions.TaskExecutionException;
import cz.metacentrum.perun.engine.jms.JMSQueueManager;
import cz.metacentrum.perun.engine.scheduling.SchedulingPool;
import cz.metacentrum.perun.taskslib.model.SendTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jms.JMSException;

import static cz.metacentrum.perun.taskslib.model.Task.TaskStatus.SENDERROR;

public class SendCollector implements Runnable {
	private final static Logger log = LoggerFactory
			.getLogger(SendCollector.class);
	@Autowired
	private BlockingSendExecutorCompletionService sendCompletionService;
	@Autowired
	private SchedulingPool schedulingPool;
	@Autowired
	private JMSQueueManager jmsQueueManager;

	@Override
	public void run() {
		while (true) {
			try {
				SendTask sendTask = sendCompletionService.blockingTake();
				schedulingPool.decreaseSendTaskCount(sendTask.getId().getLeft(), 1);
				try {
					jmsQueueManager.reportSendTask(sendTask);
				} catch (JMSException e) {
					jmsErrorLog(sendTask.getId().getLeft(), sendTask.getId().getRight());
				}
			} catch (InterruptedException e) {
				String errorStr = "Thread collecting sent SendTasks was interrupted.";
				log.error(errorStr);
				throw new RuntimeException(errorStr, e);
			} catch (TaskExecutionException e) {
				Pair<Integer, Destination> id = (Pair<Integer, Destination>) e.getId();
				try {
					jmsQueueManager.reportSendTask(id.getLeft(), SENDERROR.toString(), id.getRight().toString());
				} catch (JMSException e1) {
					jmsErrorLog(id.getLeft(), id.getRight());
				}
			}
		}
	}

	private void jmsErrorLog(Integer id, Destination destination) {
		log.warn("Could not send status update to SendTask with id {} and destination {}.", id, destination);
	}
}
