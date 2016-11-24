package cz.metacentrum.perun.engine.runners;

public interface EngineRunner {
	public boolean shouldStop();
	public void stop();
}
