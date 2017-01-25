package cz.metacentrum.perun.dispatcher.scheduling;

/**
 *
 * @author Michal Karm Babacek
 */
public interface PropagationMaintainer {

	void checkResults();

	void closeTasksForEngine(int clientID);

	void onTaskStatusChange(int taskId, String status, String date);

	void onTaskDestinationComplete(int clientID, String string);

}
