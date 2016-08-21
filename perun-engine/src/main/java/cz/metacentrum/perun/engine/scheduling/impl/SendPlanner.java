package cz.metacentrum.perun.engine.scheduling.impl;


import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.engine.jms.JMSQueueManager;
import cz.metacentrum.perun.engine.scheduling.SchedulingPool;
import cz.metacentrum.perun.engine.scheduling.SendWorker;
import cz.metacentrum.perun.taskslib.model.SendTask;
import cz.metacentrum.perun.taskslib.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jms.JMSException;
import java.util.Date;
import java.util.concurrent.BlockingQueue;

import static cz.metacentrum.perun.taskslib.model.SendTask.SendTaskStatus.SENDING;

public class SendPlanner implements Runnable {
	private final static Logger log = LoggerFactory
			.getLogger(SendPlanner.class);
	@Autowired
	private BlockingSendExecutorCompletionService sendCompletionService;
	@Autowired
	private SchedulingPool schedulingPool;
	@Autowired
	private JMSQueueManager jmsQueueManager;

	@Override
	public void run() {
		BlockingQueue<Task> generatedTasks = schedulingPool.getGeneratedTasksQueue();
		while (true) {
			try {
				Task task = generatedTasks.take();
				task.setStatus(Task.TaskStatus.SENDING);
				schedulingPool.addSendTaskCount(task.getId(), task.getDestinations().size());

				for (Destination destination : task.getDestinations()) {
					SendTask sendTask = new SendTask();
					sendTask.setTask(task);
					sendTask.setDestination(destination);
					SendWorker worker = new SendWorkerImpl(destination, sendTask);

					sendCompletionService.blockingSubmit(worker);
					sendTask.setStartTime(new Date(System.currentTimeMillis()));
					sendTask.setStatus(SENDING);
					try {
						jmsQueueManager.reportSendTask(sendTask);
					} catch (JMSException e) {
						log.warn("Could not send SendTasks [{}] status update.", sendTask);
					}
				}

			} catch (InterruptedException e) {
				String errorStr = "Thread planning SendTasks was interrupted.";
				log.error(errorStr);
				throw new RuntimeException(errorStr, e);
			}
		}
	}
}
