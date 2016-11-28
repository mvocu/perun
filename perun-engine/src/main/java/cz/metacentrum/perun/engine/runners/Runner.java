package cz.metacentrum.perun.engine.runners;

public interface Runner extends Runnable {
	public boolean shouldStop();
	public void stop();
}
