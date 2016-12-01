package cz.metacentrum.perun.taskslib.runners;

public interface Runner extends Runnable {
	public boolean shouldStop();
	public void stop();
}
