package cz.metacentrum.perun.taskslib.runners;

/**
 * Basic interface for all *Planner/Collector classes to allow easier unit testing.
 */
public interface Runner extends Runnable {
	public boolean shouldStop();
	public void stop();
}
