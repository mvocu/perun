package cz.metacentrum.perun.dispatcher.processing;

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
 * @see cz.metacentrum.perun.dispatcher.parser.AuditerListener
 * @see cz.metacentrum.perun.dispatcher.scheduling.SchedulingPool
 *
 * @author Michal Karm Babacek
 * @author Michal Vocu
 * @author David Šarman
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
public interface EventProcessor {

	/**
	 * Start processing Events waiting in EventQueue (created by AuditerListener).
	 */
	void startProcessingEvents();

	/**
	 * Stop processing Events waiting in EventQueue (created by AuditerListener).
	 */
	void stopProcessingEvents();

}
