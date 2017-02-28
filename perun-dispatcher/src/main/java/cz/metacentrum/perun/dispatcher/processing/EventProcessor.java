package cz.metacentrum.perun.dispatcher.processing;

import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Service;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PrivilegeException;
import cz.metacentrum.perun.core.api.exceptions.ServiceNotExistsException;
import cz.metacentrum.perun.dispatcher.exceptions.InvalidEventMessageException;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueuePool;
import cz.metacentrum.perun.dispatcher.model.Event;
import cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool;
import cz.metacentrum.perun.taskslib.dao.ServiceDenialDao;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.model.Task.TaskStatus;
import cz.metacentrum.perun.taskslib.runners.impl.AbstractRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * This class ensure periodic blocking polling of EventQueue with Events parsed from audit messages by AuditerListener.
 *
 * For each Event, Facility and set of affected Services is resolved. If can't be resolved or are empty, Event is discarded.
 *
 * Each Event is converted to Task if possible and added to pool (if new) or updated in pool (if exists).
 * New Tasks are also planned immediately.
 *
 * @see cz.metacentrum.perun.dispatcher.processing.EventQueue
 * @see cz.metacentrum.perun.dispatcher.model.Event
 * @see cz.metacentrum.perun.dispatcher.processing.AuditerListener
 * @see cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool
 *
 * @author Michal Karm Babacek
 * @author Michal Vocu
 * @author David Šarman
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
@org.springframework.stereotype.Service(value = "eventProcessor")
public class EventProcessor extends AbstractRunner {

	private final static Logger log = LoggerFactory.getLogger(EventProcessor.class);

	private EventQueue eventQueue;
	private DispatcherQueuePool dispatcherQueuePool;
	private EventServiceResolver eventServiceResolver;
	private ServiceDenialDao serviceDenialDao;
	private SchedulingPool schedulingPool;

	// ----- setters -------------------------------------

	public EventQueue getEventQueue() {
		return eventQueue;
	}

	@Autowired
	public void setEventQueue(EventQueue eventQueue) {
		this.eventQueue = eventQueue;
	}

	public DispatcherQueuePool getDispatcherQueuePool() {
		return dispatcherQueuePool;
	}

	@Autowired
	public void setDispatcherQueuePool(DispatcherQueuePool dispatcherQueuePool) {
		this.dispatcherQueuePool = dispatcherQueuePool;
	}

	public EventServiceResolver getEventServiceResolver() {
		return eventServiceResolver;
	}

	@Autowired
	public void setEventServiceResolver(EventServiceResolver eventServiceResolver) {
		this.eventServiceResolver = eventServiceResolver;
	}

	public ServiceDenialDao getServiceDenialDao() {
		return serviceDenialDao;
	}

	@Autowired
	public void setServiceDenialDao(ServiceDenialDao serviceDenialDao) {
		this.serviceDenialDao = serviceDenialDao;
	}

	public SchedulingPool getSchedulingPool() {
		return schedulingPool;
	}

	@Autowired
	public void setSchedulingPool(SchedulingPool schedulingPool) {
		this.schedulingPool = schedulingPool;
	}

	// ----- methods -------------------------------------

	/**
	 * EvProcessor thread, reads EventQueue and convert Events to Tasks,
	 * which are added to scheduling pool or updated if already in pool.
	 */
	@Override
	public void run() {
		while (!shouldStop()) {
			try {
				Event event = eventQueue.poll();
				if (event != null) {
					createTaskFromEvent(event);
					log.debug("Remaining events in a Queue = {}, Engines = {}", eventQueue.size(), dispatcherQueuePool.poolSize());
				}
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	/**
	 * Creates Task from Event data. Tries to resolve Service and Facility pairs from Event.
	 * Events for non existing entities are discarded.
	 *
	 * @param event Event to parse
	 * @throws ServiceNotExistsException When Service from Event doesn't exists anymore
	 * @throws InvalidEventMessageException  When Message has invalid format.
	 * @throws InternalErrorException  When implementation fails
	 * @throws PrivilegeException  When dispatcher lack privileges to call core methods
	 */
	private void createTaskFromEvent(Event event) throws ServiceNotExistsException, InvalidEventMessageException, InternalErrorException, PrivilegeException {

		Map<Facility, Set<Service>> resolvedServices = eventServiceResolver.parseEvent(event.toString());

		for (Entry<Facility, Set<Service>> map : resolvedServices.entrySet()) {
			Facility facility = map.getKey();
			for (Service service : map.getValue()) {
				if (!service.isEnabled()) {
					log.debug("Service not enabled: {}.", service);
					continue;
				}

				if (serviceDenialDao.isServiceBlockedOnFacility(service.getId(), facility.getId())) {
					log.debug("Service blocked on Facility: {} , {}.", service, facility);
					continue;
				}

				// check for presence of task for this <Service, Facility> pair
				// NOTE: this must be atomic enough to not create duplicate
				// tasks in schedulingPool (are we running in parallel
				// here?)
				Task task = schedulingPool.getTask(facility, service);
				if (task != null) {
					// there already is a task in schedulingPool
					// signal that task needs to regenerate data
					task.setDestinations(null);
					task.setSourceUpdated(true);
					// FIXME - how we can handle fact, that Task is already planned/processing and forced flag is meant for next processing
					// FIXME - also we don't want normal event to cancel our forced flag from previous runs.
					task.setPropagationForced(determineForcedPropagation(event));
					task.setRecurrence(0);
					log.debug("[{}] Task is already in pool. Re-setting source updated and forced flags, {}.", task.getId(), task);
				} else {
					// no such task yet, create one
					task = new Task();
					task.setFacility(facility);
					task.setService(service);
					task.setStatus(TaskStatus.WAITING);
					task.setRecurrence(0);
					task.setSchedule(new Date(System.currentTimeMillis()));
					task.setSourceUpdated(false);
					boolean isForced = determineForcedPropagation(event);
					task.setPropagationForced(isForced);
					try {
						schedulingPool.addToPool(task, null);
						log.debug("[{}] New Task added to pool. {}.", task.getId(), task);
					} catch (TaskStoreException e) {
						log.error("[{}] Could not add Task to pool. Task will be lost. {}.", task.getId(), task);
					}
					// forced has zero delay (with zero time too), normal has default delay count
					schedulingPool.scheduleTask(task, (isForced) ? 0 : -1);
				}
			}
		}
	}

	/**
	 * Return true if event forces service propagation
	 *
	 * @param event Event to check.
	 * @return TRUE = forced propagation / FALSE = normal data change
	 */
	private boolean determineForcedPropagation(Event event) {
		return event.getData().contains("force propagation:");
	}

}
