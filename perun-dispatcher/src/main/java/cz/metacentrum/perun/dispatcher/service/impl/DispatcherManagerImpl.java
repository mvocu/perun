package cz.metacentrum.perun.dispatcher.service.impl;

import cz.metacentrum.perun.dispatcher.parser.AuditerListener;
import cz.metacentrum.perun.dispatcher.scheduling.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.metacentrum.perun.dispatcher.exceptions.PerunHornetQServerException;
import cz.metacentrum.perun.dispatcher.hornetq.PerunHornetQServer;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueue;
import cz.metacentrum.perun.dispatcher.jms.SystemQueueProcessor;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueuePool;
import cz.metacentrum.perun.dispatcher.processing.EventProcessor;
import cz.metacentrum.perun.dispatcher.processing.SmartMatcher;
import cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool;
import cz.metacentrum.perun.dispatcher.service.DispatcherManager;
import cz.metacentrum.perun.taskslib.service.ResultManager;
import org.springframework.core.task.TaskExecutor;

/**
 * Implementation of DispatcherManager.
 *
 * @author Michal Karm Babacek
 */
@org.springframework.stereotype.Service(value = "dispatcherManager")
public class DispatcherManagerImpl implements DispatcherManager {
	private final static Logger log = LoggerFactory.getLogger(DispatcherManagerImpl.class);

	@Autowired
	private PerunHornetQServer perunHornetQServer;
	@Autowired
	private SystemQueueProcessor systemQueueProcessor;
	@Autowired
	private EventProcessor eventProcessor;
	@Autowired
	private SmartMatcher smartMatcher;
	@Autowired
	private SchedulingPool schedulingPool;
	@Autowired
	private DispatcherQueuePool dispatcherQueuePool;
	@Autowired
	private ResultManager resultManager;
	@Autowired
	private TaskScheduler taskScheduler;
	@Autowired
	private TaskExecutor taskExecutor;
	@Autowired
	private AuditerListener auditerListener;

	@Override
	public void startPerunHornetQServer() {
		perunHornetQServer.startServer();
	}

	@Override
	public void stopPerunHornetQServer() {
		perunHornetQServer.stopServer();
	}

	@Override
	public void prefetchRulesAndDispatcherQueues() throws PerunHornetQServerException {
		smartMatcher.loadAllRulesFromDB();
		systemQueueProcessor.createDispatcherQueuesForClients(smartMatcher.getClientsWeHaveRulesFor());
	}

	@Override
	public void startProcessingSystemMessages() {
		systemQueueProcessor.startProcessingSystemMessages();
	}

	@Override
	public void stopProcessingSystemMessages() {
		systemQueueProcessor.stopProcessingSystemMessages();
	}

	@Override
	public void startAuditerListener() {
		taskExecutor.execute(new Runnable() {
			@Override
			public void run() {
				auditerListener.init();
			}
		});
	}

	@Override
	public void stopAuditerListener() {
		// TODO - not implemeneted
	}

	@Override
	public void startProcessingEvents() {
		eventProcessor.startProcessingEvents();
	}

	@Override
	public void stopProcessingEvents() {
		eventProcessor.stopProcessingEvents();
	}

	public void setPerunHornetQServer(PerunHornetQServer perunHornetQServer) {
		this.perunHornetQServer = perunHornetQServer;
	}

	public void setSystemQueueProcessor(
			SystemQueueProcessor systemQueueProcessor) {
		this.systemQueueProcessor = systemQueueProcessor;
	}

	public void setTaskExecutor(TaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	public void setAuditerListener(AuditerListener auditerListener) {
		this.auditerListener = auditerListener;
	}

	public void setEventProcessor(EventProcessor eventProcessor) {
		this.eventProcessor = eventProcessor;
	}

	public void setSmartMatcher(SmartMatcher smartMatcher) {
		this.smartMatcher = smartMatcher;
	}

	@Override
	public void loadSchedulingPool() {
		schedulingPool.reloadTasks();
	}

	@Override
	public void cleanOldTaskResults() {
		for(DispatcherQueue queue: dispatcherQueuePool.getPool()) {
			try {
				int numRows = resultManager.clearOld(queue.getClientID(), 3);
				log.debug("Cleaned {} old task results for engine {}", numRows, queue.getClientID());
			} catch (Throwable e) {
				log.error("Error cleaning old task results for engine {} : {}", queue.getClientID(), e);
			}
		}
	}

	@Override
	public void startSchedulingTasks() {
		Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread thread, Throwable throwable) {
				log.error("Unknown exception was caught in TaskScheduler", throwable);
			}
		};

		Thread t = new Thread(taskScheduler);
		t.setUncaughtExceptionHandler(h);
		t.start();
	}

}
