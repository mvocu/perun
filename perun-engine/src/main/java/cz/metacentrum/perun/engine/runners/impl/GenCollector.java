package cz.metacentrum.perun.engine.runners.impl;


import cz.metacentrum.perun.engine.exceptions.TaskExecutionException;
import cz.metacentrum.perun.engine.jms.JMSQueueManager;
import cz.metacentrum.perun.engine.scheduling.SchedulingPool;
import cz.metacentrum.perun.engine.scheduling.impl.BlockingGenExecutorCompletionService;
import cz.metacentrum.perun.taskslib.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.jms.JMSException;
import java.util.concurrent.BlockingDeque;

import static cz.metacentrum.perun.taskslib.model.Task.TaskStatus.GENERROR;

public class GenCollector extends AbstractRunner {
	private final static Logger log = LoggerFactory
			.getLogger(GenCollector.class);
	@Autowired
	private SchedulingPool schedulingPool;
	@Autowired
	private BlockingGenExecutorCompletionService genCompletionService;
	@Autowired
	private JMSQueueManager jmsQueueManager;

	public GenCollector() {
	}

	public GenCollector(SchedulingPool schedulingPool, BlockingGenExecutorCompletionService genCompletionService, JMSQueueManager jmsQueueManager) {
		this.schedulingPool = schedulingPool;
		this.genCompletionService = genCompletionService;
		this.jmsQueueManager = jmsQueueManager;
	}

	@Override
	public void run() {
		BlockingDeque<Task> generatedTasks = schedulingPool.getGeneratedTasksQueue();
		while (!shouldStop()) {
			try {
				Task task = genCompletionService.blockingTake();
				if (task.isPropagationForced()) {
					generatedTasks.putFirst(task);
				} else {
					generatedTasks.put(task);
				}
				try {
					jmsQueueManager.reportGenTask(task);
				} catch (JMSException e) {
					jmsErrorLog(task.getId());
				}
			} catch (InterruptedException e) {
				String errorStr = "Thread collecting generated Tasks was interrupted.";
				log.error(errorStr);
				throw new RuntimeException(errorStr, e);
			} catch (TaskExecutionException e) {
				Integer id = (Integer) e.getId();
				try {
					jmsQueueManager.reportGenTask(id, GENERROR.toString());
				} catch (JMSException e1) {
					jmsErrorLog(id);
				}
				schedulingPool.removeTask(id);
			}
		}
	}

	private void jmsErrorLog(Integer id) {
		log.warn("Could not send GEN status update to task with id {}.", id);
	}
}
