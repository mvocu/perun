package cz.metacentrum.perun.dispatcher.processing.impl;

import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PrivilegeException;
import cz.metacentrum.perun.core.api.exceptions.ServiceNotExistsException;
import cz.metacentrum.perun.dispatcher.exceptions.InvalidEventMessageException;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueue;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueuePool;
import cz.metacentrum.perun.dispatcher.model.Event;
import cz.metacentrum.perun.dispatcher.processing.EventExecServiceResolver;
import cz.metacentrum.perun.dispatcher.processing.EventLogger;
import cz.metacentrum.perun.dispatcher.processing.EventProcessor;
import cz.metacentrum.perun.dispatcher.processing.EventQueue;
import cz.metacentrum.perun.dispatcher.scheduling.DenialsResolver;
import cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool;
import cz.metacentrum.perun.dispatcher.scheduling.TaskScheduler;
import cz.metacentrum.perun.taskslib.model.ExecService;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.model.Task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * This class takes Tasks parsed from auditer and inserts them into schedulingPool into proper queue,
 * so it gets sent into Engine.
 *
 * @author Michal Karm Babacek
 */
@org.springframework.stereotype.Service(value = "eventProcessor")
public class EventProcessorImpl implements EventProcessor {

	private final static Logger log = LoggerFactory
			.getLogger(EventProcessorImpl.class);

	@Autowired
	private EventQueue eventQueue;
	@Autowired
	private DispatcherQueuePool dispatcherQueuePool;
	@Autowired
	private EventLogger eventLogger;
	@Autowired
	private TaskExecutor taskExecutor;
	private EvProcessor evProcessor;
	@Autowired
	private EventExecServiceResolver eventExecServiceResolver;
	@Autowired
	private DenialsResolver denialsResolver;
	@Autowired
	private SchedulingPool schedulingPool;
	@Autowired
	private TaskScheduler taskScheduler;

	@Override
	public void startProcessingEvents() {
		try {
			evProcessor = new EvProcessor();
			taskExecutor.execute(evProcessor);
		} catch (Exception e) {
			log.error(e.toString(), e);
		}
	}

	@Override
	public void stopProcessingEvents() {
		evProcessor.stop();
	}

	public EventQueue getEventQueue() {
		return eventQueue;
	}

	public void setEventQueue(EventQueue eventQueue) {
		this.eventQueue = eventQueue;
	}

	public DispatcherQueuePool getDispatcherQueuePool() {
		return dispatcherQueuePool;
	}

	public void setDispatcherQueuePool(DispatcherQueuePool dispatcherQueuePool) {
		this.dispatcherQueuePool = dispatcherQueuePool;
	}

	public EventLogger getEventLogger() {
		return eventLogger;
	}

	public void setEventLogger(EventLogger eventLogger) {
		this.eventLogger = eventLogger;
	}

	public TaskExecutor getTaskExecutor() {
		return taskExecutor;
	}

	public void setTaskExecutor(TaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	public EvProcessor getEvProcessor() {
		return evProcessor;
	}

	public void setEvProcessor(EvProcessor evProcessor) {
		this.evProcessor = evProcessor;
	}

	public EventExecServiceResolver getEventExecServiceResolver() {
		return eventExecServiceResolver;
	}

	public void setEventExecServiceResolver(
			EventExecServiceResolver eventExecServiceResolver) {
		this.eventExecServiceResolver = eventExecServiceResolver;
	}

	public DenialsResolver getDenialsResolver() {
		return denialsResolver;
	}

	public void setDenialsResolver(DenialsResolver denialsResolver) {
		this.denialsResolver = denialsResolver;
	}

	public SchedulingPool getSchedulingPool() {
		return schedulingPool;
	}

	public void setSchedulingPool(SchedulingPool schedulingPool) {
		this.schedulingPool = schedulingPool;
	}

	public class EvProcessor implements Runnable {
		private boolean running = true;

		@Override
		public void run() {
			while (running) {
				try {
					Event event = eventQueue.poll();
					if (event != null) {
						log.debug("Number of: Events in Queue = {}, Engines = {}",
								eventQueue.size(), dispatcherQueuePool.poolSize());
						createTask(null, event);
						eventLogger.logEvent(event, -1);
					}
				} catch (Exception e) {
					log.error(e.getMessage(), e);
				}
			}
		}

		private void createTask(DispatcherQueue dispatcherQueue, Event event)
				throws ServiceNotExistsException, InvalidEventMessageException,
				InternalErrorException, PrivilegeException {
			// Resolve the services in event, send the resulting <ExecService,
			// Facility> pairs to engine
			Map<Facility, Set<ExecService>> resolvedServices = eventExecServiceResolver
					.parseEvent(event.toString());
			for (Entry<Facility, Set<ExecService>> service : resolvedServices.entrySet()) {
				Facility facility = service.getKey();
				for (ExecService execService : service.getValue()) {
					if (!execService.isEnabled()) {
						log.debug("ExecService {} is not enabled globally", execService);
						continue;
					}

					if (denialsResolver.isExecServiceDeniedOnFacility(execService, facility)) {
						log.debug("ExecService {} is denied on Facility {}", execService, facility);
						continue;
					}

					// check for presence of task for this <execService,
					// facility> pair
					// NOTE: this must be atomic enough to not create duplicate
					// tasks in schedulingPool (are we running in parallel
					// here?)
					Task task = schedulingPool.getTask(facility, execService);
					if (task != null) {
						// there already is a task in schedulingPool
						log.debug("Task is in the pool already.\n" +
								"Removing destinations from existing task to re-fetch them later on.");
						task.setDestinations(null);
						// signal that task needs to regenerate data
						task.setSourceUpdated(true);
						task.setPropagationForced(determineForcedPropagation(event));
						task.setRecurrence(0);
					} else {
						// no such task yet, create one
						task = new Task();
						task.setFacility(facility);
						task.setExecService(execService);
						task.setStatus(TaskStatus.WAITING);
						task.setRecurrence(0);
						task.setSchedule(new Date(System.currentTimeMillis()));
						task.setSourceUpdated(false);
						task.setPropagationForced(determineForcedPropagation(event));
						schedulingPool.addToPool(task, dispatcherQueue);
						schedulingPool.addTaskSchedule(task, -1);
						log.debug("Created new task {} and added it to the pool.", task);
					}
				}
			}
		}

		private boolean determineForcedPropagation(Event event) {
			return event.getData().contains("force propagation:");
		}

		public void stop() {
			running = false;
		}
	}

}
