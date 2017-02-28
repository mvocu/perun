package cz.metacentrum.perun.dispatcher.scheduling;

/**
 * Manage Tasks (all propagations) in scheduling pool, mainly reschedules Tasks when
 * - source was updated
 * - ended in error
 * - didn't run for 2 days
 * - seems to be stuck in processing state (aprox. 3 hours)
 *
 * @author Michal Karm Babacek
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
public interface PropagationMaintainer {

	/**
	 * Check propagation results and reschedule necessary Tasks when
	 * - source was updated
	 * - ended in error
	 * - didn't run for 2 days
	 * - seems to be stuck in processing state (aprox. 3 hours)
	 */
	void checkResults();

	/**
	 * Switch all processing Tasks to ERROR if engine was restarted.
	 *
	 * @param clientID ID of Engine
	 */
	void closeTasksForEngine(int clientID);

	/**
	 * Store change in Task status sent from Engine.
	 *
	 * @param taskId ID of Task to update
	 * @param status TaskStatus to set
	 * @param date Timestamp of change (string)
	 */
	void onTaskStatusChange(int taskId, String status, String date);

	/**
	 * Store TaskResult sent from Engine.
	 *
	 * @param clientID ID of Engine
	 * @param string Serialized TaskResult object
	 */
	void onTaskDestinationComplete(int clientID, String string);

}
