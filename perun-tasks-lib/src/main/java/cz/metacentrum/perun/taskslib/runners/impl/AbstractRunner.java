package cz.metacentrum.perun.taskslib.runners.impl;

import cz.metacentrum.perun.taskslib.runners.Runner;

public abstract class AbstractRunner implements Runner {
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
