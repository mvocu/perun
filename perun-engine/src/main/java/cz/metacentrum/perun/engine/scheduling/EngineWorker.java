package cz.metacentrum.perun.engine.scheduling;

import java.util.concurrent.Callable;


public interface EngineWorker<V> extends Callable<V> {
	@Override
	V call() throws Exception;
}
