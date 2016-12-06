package cz.metacentrum.perun.engine.runners;


import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.engine.exceptions.TaskExecutionException;
import cz.metacentrum.perun.engine.jms.JMSQueueManager;
import cz.metacentrum.perun.engine.scheduling.SchedulingPool;
import cz.metacentrum.perun.engine.scheduling.impl.BlockingSendExecutorCompletionService;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.model.SendTask;
import cz.metacentrum.perun.taskslib.runners.impl.AbstractRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jms.JMSException;
import java.util.Date;

import static cz.metacentrum.perun.taskslib.model.SendTask.SendTaskStatus.ERROR;
import static cz.metacentrum.perun.taskslib.model.SendTask.SendTaskStatus.SENT;

/**
 * This class takes all executed SendTasks (both successfully and not) and reports their state to the Dispatcher.
 * It also watches for the number of SendTasks associated with their parent Task, which will be removed from Engine
 * once all its SendTask are executed.
 */
public class SendCollector extends AbstractRunner {
	private final static Logger log = LoggerFactory
			.getLogger(SendCollector.class);
	@Autowired
	private BlockingSendExecutorCompletionService sendCompletionService;
	@Autowired
	private SchedulingPool schedulingPool;
	@Autowired
	private JMSQueueManager jmsQueueManager;

	public SendCollector() {
	}

	public SendCollector(BlockingSendExecutorCompletionService sendCompletionService, SchedulingPool schedulingPool, JMSQueueManager jmsQueueManager) {
		this.sendCompletionService = sendCompletionService;
		this.schedulingPool = schedulingPool;
		this.jmsQueueManager = jmsQueueManager;
	}

	@Override
	public void run() {
		while (!shouldStop()) {
			try {
				SendTask sendTask = sendCompletionService.blockingTake();
				sendTask.setStatus(SENT);
				sendTask.setEndTime(new Date(System.currentTimeMillis()));
				schedulingPool.decreaseSendTaskCount(sendTask.getId().getLeft(), 1);
				try {
					jmsQueueManager.reportSendTaskStatus(sendTask.getTask().getId(), sendTask.getStatus(),
							sendTask.getDestination(), sendTask.getEndTime());
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
					jmsQueueManager.reportSendTaskStatus(id.getLeft(), ERROR, id.getRight(),
							new Date(System.currentTimeMillis()));
				} catch (JMSException e1) {
					jmsErrorLog(id.getLeft(), id.getRight());
				}
			} catch (TaskStoreException e) {
				log.error("Task {} could not be removed from SchedulingPool", e);
			}
		}
	}

	private void jmsErrorLog(Integer id, Destination destination) {
		log.warn("Could not send status update to SendTask with id {} and destination {}.", id, destination);
	}
}
