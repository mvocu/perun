package cz.metacentrum.perun.dispatcher.service.impl;

import cz.metacentrum.perun.dispatcher.exceptions.PerunHornetQServerException;
import cz.metacentrum.perun.dispatcher.hornetq.PerunHornetQServer;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueue;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueuePool;
import cz.metacentrum.perun.dispatcher.jms.SystemQueueProcessor;
import cz.metacentrum.perun.dispatcher.job.CleanTaskResultsJob;
import cz.metacentrum.perun.dispatcher.job.PropagationMaintainerJob;
import cz.metacentrum.perun.dispatcher.parser.AuditerListener;
import cz.metacentrum.perun.dispatcher.processing.EventProcessor;
import cz.metacentrum.perun.dispatcher.processing.SmartMatcher;
import cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool;
import cz.metacentrum.perun.dispatcher.scheduling.TaskScheduler;
import cz.metacentrum.perun.dispatcher.service.DispatcherManager;
import cz.metacentrum.perun.taskslib.service.ResultManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;

import javax.annotation.PreDestroy;
import java.util.Properties;

/**
 * Implementation of DispatcherManager.
 *
 * @author Michal Karm Babacek
 * @author Michal Voců
 * @author David Šarman
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
public class DispatcherManagerImpl implements DispatcherManager {

	private final static Logger log = LoggerFactory.getLogger(DispatcherManagerImpl.class);

	private PerunHornetQServer perunHornetQServer;
	private SystemQueueProcessor systemQueueProcessor;
	private EventProcessor eventProcessor;
	private SmartMatcher smartMatcher;
	private SchedulingPool schedulingPool;
	private DispatcherQueuePool dispatcherQueuePool;
	private ResultManager resultManager;
	private TaskScheduler taskScheduler;
	private TaskExecutor taskExecutor;
	private AuditerListener auditerListener;
	private Properties dispatcherProperties;
	private PropagationMaintainerJob propagationMaintainerJob;
	private CleanTaskResultsJob cleanTaskResultsJob;


	// ----- setters -------------------------------------


	public PerunHornetQServer getPerunHornetQServer() {
		return perunHornetQServer;
	}

	@Autowired
	public void setPerunHornetQServer(PerunHornetQServer perunHornetQServer) {
		this.perunHornetQServer = perunHornetQServer;
	}

	public SystemQueueProcessor getSystemQueueProcessor() {
		return systemQueueProcessor;
	}

	@Autowired
	public void setSystemQueueProcessor(SystemQueueProcessor systemQueueProcessor) {
		this.systemQueueProcessor = systemQueueProcessor;
	}

	public EventProcessor getEventProcessor() {
		return eventProcessor;
	}

	@Autowired
	public void setEventProcessor(EventProcessor eventProcessor) {
		this.eventProcessor = eventProcessor;
	}

	public SmartMatcher getSmartMatcher() {
		return smartMatcher;
	}

	@Autowired
	public void setSmartMatcher(SmartMatcher smartMatcher) {
		this.smartMatcher = smartMatcher;
	}

	public SchedulingPool getSchedulingPool() {
		return schedulingPool;
	}

	@Autowired
	public void setSchedulingPool(SchedulingPool schedulingPool) {
		this.schedulingPool = schedulingPool;
	}

	public DispatcherQueuePool getDispatcherQueuePool() {
		return dispatcherQueuePool;
	}

	@Autowired
	public void setDispatcherQueuePool(DispatcherQueuePool dispatcherQueuePool) {
		this.dispatcherQueuePool = dispatcherQueuePool;
	}

	public ResultManager getResultManager() {
		return resultManager;
	}

	@Autowired
	public void setResultManager(ResultManager resultManager) {
		this.resultManager = resultManager;
	}

	public TaskScheduler getTaskScheduler() {
		return taskScheduler;
	}

	@Autowired
	public void setTaskScheduler(TaskScheduler taskScheduler) {
		this.taskScheduler = taskScheduler;
	}

	public TaskExecutor getTaskExecutor() {
		return taskExecutor;
	}

	@Autowired
	public void setTaskExecutor(TaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	public AuditerListener getAuditerListener() {
		return auditerListener;
	}

	@Autowired
	public void setAuditerListener(AuditerListener auditerListener) {
		this.auditerListener = auditerListener;
	}

	public PropagationMaintainerJob getPropagationMaintainerJob() {
		return propagationMaintainerJob;
	}

	public Properties getDispatcherProperties() {
		return dispatcherProperties;
	}

	@Autowired
	public void setDispatcherProperties(Properties dispatcherProperties) {
		this.dispatcherProperties = dispatcherProperties;
	}

	@Autowired
	public void setPropagationMaintainerJob(PropagationMaintainerJob propagationMaintainerJob) {
		this.propagationMaintainerJob = propagationMaintainerJob;
	}

	public CleanTaskResultsJob getCleanTaskResultsJob() {
		return cleanTaskResultsJob;
	}

	@Autowired
	public void setCleanTaskResultsJob(CleanTaskResultsJob cleanTaskResultsJob) {
		this.cleanTaskResultsJob = cleanTaskResultsJob;
	}


	// ----- methods -------------------------------------


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
		taskExecutor.execute(auditerListener);
	}

	@Override
	public void stopAuditerListener() {
		auditerListener.stop();
	}

	@Override
	public void startProcessingEvents() {
		eventProcessor.startProcessingEvents();
	}

	@Override
	public void stopProcessingEvents() {
		eventProcessor.stopProcessingEvents();
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
	public void startTasksScheduling() {

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

	/**
	 * Main initialization method. Loads all data and starts all scheduling a processing threads.
	 */
	public final void init() {

		String dispatcherEnabled = dispatcherProperties.getProperty("dispatcher.enabled");

		// skip start of HornetQ and other dispatcher jobs if dispatcher is disabled
		if(dispatcherEnabled != null && !Boolean.parseBoolean(dispatcherEnabled)) {
			propagationMaintainerJob.setEnabled(false);
			cleanTaskResultsJob.setEnabled(false);
			log.debug("Perun-Dispatcher startup disabled by configuration.");
			return;
		}

		// dispatcher is enabled

		try {

			// Start HornetQ server
			startPerunHornetQServer();
			// Start System Queue Processor
			startProcessingSystemMessages();
			// Prefetch rules for all the Engines in the Perun DB and create
			prefetchRulesAndDispatcherQueues();
			// Reload tasks from database
			loadSchedulingPool();
			// Start parsers (mining data both from Grouper and PerunDB)
			startAuditerListener();
			// Start Event Processor
			startProcessingEvents();
			// Start thread for Task scheduling
			startTasksScheduling();

			log.info("Perun-Dispatcher has started.");

		} catch (Exception e) {
			log.error("Unable to start Perun-Dispatcher: {}.", e);
		}

	}

	/**
	 * Stop all processing when application is shut down.
	 */
	@PreDestroy
	public void destroy() {
		// stop current scheduler
		propagationMaintainerJob.setEnabled(false);
		cleanTaskResultsJob.setEnabled(false);
		// stop currently running jobs
		stopAuditerListener();
		stopProcessingEvents();
		stopProcessingSystemMessages();
		stopPerunHornetQServer();
	}

}
