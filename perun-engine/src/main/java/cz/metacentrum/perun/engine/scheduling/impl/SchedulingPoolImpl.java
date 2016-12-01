package cz.metacentrum.perun.engine.scheduling.impl;

import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.engine.scheduling.BlockingBoundedMap;
import cz.metacentrum.perun.engine.scheduling.SchedulingPool;
import cz.metacentrum.perun.taskslib.service.TaskStore;
import cz.metacentrum.perun.taskslib.model.ExecService;
import cz.metacentrum.perun.taskslib.model.SendTask;
import cz.metacentrum.perun.taskslib.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

import static cz.metacentrum.perun.taskslib.model.Task.TaskStatus.PLANNED;

@org.springframework.stereotype.Service(value = "schedulingPool")
public class SchedulingPoolImpl implements SchedulingPool {
	private final static Logger log = LoggerFactory.getLogger(SchedulingPoolImpl.class);
	private final ConcurrentMap<Integer, Future<Task>> genTaskFutures = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, ConcurrentMap<Destination, Future<SendTask>>> sendTasks = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, Integer> sendTaskCount = new ConcurrentHashMap<>();
	private final BlockingDeque<Task> newTasksQueue = new LinkedBlockingDeque<>();
	private final BlockingDeque<Task> generatedTasksQueue = new LinkedBlockingDeque<>();
	@Autowired
	private TaskStore taskStore;
	@Autowired
	private BlockingBoundedMap<Integer, Task> generatingTasks;
	@Autowired
	private BlockingBoundedMap<Pair<Integer, Destination>, SendTask> sendingSendTasks;

	public SchedulingPoolImpl() {
	}

	public SchedulingPoolImpl(TaskStore taskStore, BlockingBoundedMap<Integer, Task> generatingTasks, BlockingBoundedMap<Pair<Integer, Destination>, SendTask> sendingSendTasks) {
		this.taskStore = taskStore;
		this.generatingTasks = generatingTasks;
		this.sendingSendTasks = sendingSendTasks;
	}

	public Future<Task> addGenTaskFutureToPool(Integer id, Future<Task> taskFuture) {
		return genTaskFutures.put(id, taskFuture);
	}

	@Override
	public Task getTask(int id) {
		return taskStore.getTask(id);
	}

	@Override
	public Task getTask(Facility facility, ExecService execService) {
		return taskStore.getTask(facility, execService);
	}

	@Override
	public int getSize() {
		return taskStore.getSize();
	}

	/**
	 * Adds new Task to the SchedulingPool.
	 * Only newly received Tasks with PLANNED status can be added.
	 *
	 * @param task Task that will be added to the pool.
	 * @return Task that was added to the pool.
	 */
	public Task addToPool(Task task) throws TaskStoreException {
		if (task.getStatus() != PLANNED) {
			throw new IllegalArgumentException("Only Tasks with PLANNED status can be added to SchedulingPool");
		}

		Task addedTask = taskStore.addToPool(task);
		if (task.isPropagationForced()) {
			try {
				newTasksQueue.putFirst(task);
			} catch (InterruptedException e) {
				handleInterruptedException(task, e);
			}
		} else {
			try {
				newTasksQueue.put(task);
			} catch (InterruptedException e) {
				handleInterruptedException(task, e);
			}
		}
		return addedTask;
	}

	@Override
	public List<Task> getTasksWithStatus(Task.TaskStatus status) {
		return taskStore.getTasksWithStatus(status);
	}

	@Override
	public Integer addSendTaskCount(int taskId, int count) {
		return sendTaskCount.put(taskId, count);
	}

	@Override
	public Integer decreaseSendTaskCount(int taskId, int decrease) throws TaskStoreException {
		Integer count = sendTaskCount.get(taskId);
		if (count == null) {
			return null;
		} else if (count <= 1) {
			Task removed = removeTask(taskId);
			return 0;
		} else {
			return sendTaskCount.replace(taskId, count - decrease);
		}
	}

	private <E> List<E> getFromIterator(Iterator<E> iterator) {
		List<E> list = new ArrayList<>();
		while (iterator.hasNext()) {
			list.add(iterator.next());
		}
		return list;
	}

	@Override
	public BlockingDeque<Task> getNewTasksQueue() {
		return newTasksQueue;
	}

	@Override
	public BlockingDeque<Task> getGeneratedTasksQueue() {
		return generatedTasksQueue;
	}

	@Override
	public BlockingBoundedMap<Integer, Task> getGeneratingTasksBlockingMap() {
		return generatingTasks;
	}

	@Override
	public BlockingBoundedMap<Pair<Integer, Destination>, SendTask> getSendingSendTasksBlockingMap() {
		return sendingSendTasks;
	}

	@Override
	public ConcurrentMap<Integer, Future<Task>> getGenTaskFuturesMap() {
		return genTaskFutures;
	}

	@Override
	public Future<Task> getGenTaskFutureById(int id) {
		return genTaskFutures.get(id);
	}

	@Override
	public Task removeTask(Task task) throws TaskStoreException {
		return removeTask(task.getId());
	}

	@Override
	public Task removeTask(int id) throws TaskStoreException {
		Task removed = taskStore.removeTask(id);
		Future<Task> taskFuture = genTaskFutures.get(id);
		if (taskFuture != null) {
			taskFuture.cancel(true);
		}
		if (removed != null) {
			cancelSendTasks(id);
			sendTaskCount.remove(id);
		}
		return removed;
	}

	public Future<SendTask> removeSendTaskFuture(int taskId, Destination destination) throws TaskStoreException {
		ConcurrentMap<Destination, Future<SendTask>> destinationSendTasks = sendTasks.get(taskId);
		if (destinationSendTasks != null) {
			Future<SendTask> removed = destinationSendTasks.remove(destination);
			if (removed != null) {
				decreaseSendTaskCount(taskId, 1);
			}
			return removed;
		} else {
			return null;
		}
	}

	private void handleInterruptedException(Task task, InterruptedException e) {
		String errorMessage = "Thread was interrupted while trying to put Task " + task + " into new Tasks queue.";
		log.error(errorMessage, e);
		//TODO: Is this the correct behaviour here? This should not happen.
		throw new RuntimeException(errorMessage, e);
	}

	private void cancelSendTasks(int taskId) {
		//TODO: If SendPlanner is currently planning the Task, we may not cancel all sendTasks
		ConcurrentMap<Destination, Future<SendTask>> futureSendTasks = sendTasks.get(taskId);
		if (futureSendTasks == null) {
			return;
		}
		for (Future<SendTask> sendTaskFuture : futureSendTasks.values()) {
			//TODO: Set the with interrupt parameter to true or not?
			sendTaskFuture.cancel(true);
		}
	}
}
