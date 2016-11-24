package cz.metacentrum.perun.engine.runners.impl;

import cz.metacentrum.perun.engine.runners.EngineRunner;

public abstract class AbstractEngineRunner implements EngineRunner{
	private boolean stop = false;

	@Override
	public boolean shouldStop() {
		return stop;
	}

	@Override
	public void stop() {
		stop = true;
	}
}
