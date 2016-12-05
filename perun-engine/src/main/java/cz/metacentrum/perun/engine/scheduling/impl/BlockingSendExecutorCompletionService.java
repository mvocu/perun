package cz.metacentrum.perun.engine.scheduling.impl;

import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.engine.exceptions.TaskExecutionException;
import cz.metacentrum.perun.engine.scheduling.BlockingBoundedMap;
import cz.metacentrum.perun.engine.scheduling.BlockingCompletionService;
import cz.metacentrum.perun.engine.scheduling.EngineWorker;
import cz.metacentrum.perun.engine.scheduling.SendWorker;
import cz.metacentrum.perun.taskslib.model.SendTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;


public class BlockingSendExecutorCompletionService implements BlockingCompletionService<SendTask> {
	private final static Logger log = LoggerFactory
			.getLogger(BlockingSendExecutorCompletionService.class);
	private CompletionService<SendTask> completionService;
	private BlockingBoundedMap<Pair<Integer, Destination>, SendTask> executingTasks;

	public BlockingSendExecutorCompletionService(Executor executor, BlockingQueue blockingQueue, int limit) {
		completionService = new ExecutorCompletionService<>(executor, blockingQueue);
		executingTasks = new BlockingBoundedHashMap<>(limit);
	}

	@Override
	public Future<SendTask> blockingSubmit(EngineWorker<SendTask> taskWorker) throws InterruptedException {
		SendWorker sendWorker = (SendWorker) taskWorker;
		executingTasks.blockingPut(sendWorker.getSendTask().getId(), sendWorker.getSendTask());
		return completionService.submit(sendWorker);
	}

	public SendTask blockingTake() throws InterruptedException, TaskExecutionException {
		Future<SendTask> taskFuture = completionService.take();
		try {
			SendTask taskResult = taskFuture.get();
			SendTask removed = executingTasks.remove(taskResult.getId());
			if (removed == null) {
				String errorStr = "SendTask " + taskResult + " could not be removed from completion services pool " + completionService;
				log.error(errorStr);
				throw new TaskExecutionException(taskResult.getId(), errorStr);
			}
			return taskResult;
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause.getClass().isInstance(TaskExecutionException.class)) {
				TaskExecutionException castedCause = (TaskExecutionException) cause;
				executingTasks.remove((Pair<Integer, Destination>) castedCause.getId());
			} else {
				String errorMsg = "Unexpected exception occurred during SendTask execution";
				log.error(errorMsg, e);
				throw new RuntimeException(errorMsg, e);
			}
		}
		return null;
	}
}
