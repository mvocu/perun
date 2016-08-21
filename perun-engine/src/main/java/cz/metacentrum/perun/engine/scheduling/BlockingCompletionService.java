package cz.metacentrum.perun.engine.scheduling;

import cz.metacentrum.perun.engine.exceptions.TaskExecutionException;

import java.util.concurrent.Future;

/**
 * This class wraps java.util.concurrent.CompletionService
 */
public interface BlockingCompletionService<V>{
	Future<V> blockingSubmit(EngineWorker<V> taskWorker) throws InterruptedException;

	V blockingTake() throws InterruptedException, TaskExecutionException;
}
