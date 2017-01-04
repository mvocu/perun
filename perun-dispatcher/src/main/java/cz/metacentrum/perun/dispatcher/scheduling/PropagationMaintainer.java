package cz.metacentrum.perun.dispatcher.scheduling;

import java.util.List;

import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.taskslib.model.ExecService;

/**
 * 
 * @author Michal Karm Babacek
 */
public interface PropagationMaintainer {

	void checkResults();

	void closeTasksForEngine(int clientID);

	void onTaskComplete(int parseInt, int clientID, String status, String endTimestamp, String string);

	void onTaskDestinationComplete(int clientID, String string);

}
