package cz.metacentrum.perun.engine.scheduling.impl;

import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.engine.scheduling.BlockingBoundedMap;
import cz.metacentrum.perun.engine.scheduling.SchedulingPool;
import cz.metacentrum.perun.taskslib.model.SendTask;
import cz.metacentrum.perun.taskslib.model.Task;
import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;

@org.springframework.stereotype.Service(value = "schedulingPool")
public class SchedulingPoolImpl implements SchedulingPool {
	private final static Logger log = LoggerFactory.getLogger(SchedulingPoolImpl.class);
	private final ConcurrentMap<Integer, Task> tasks = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, Future<Task>> genTaskFutures = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, ConcurrentMap<Destination, Future<SendTask>>> sendTasks = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, Integer> sendTaskCount = new ConcurrentHashMap<>();
	private final BlockingDeque<Task> newTasksQueue = new LinkedBlockingDeque<>();
	private final BlockingDeque<Task> generatedTasksQueue = new LinkedBlockingDeque<>();
	@Autowired
	private BlockingBoundedMap<Integer, Task> generatingTasks;
	@Autowired
	private BlockingBoundedMap<Pair<Integer, Destination>, SendTask> sendingSendTasks;

	@Override
	public int getSize() {
		//TODO: count tasks, sendTasks or both?
		return tasks.size();
	}

	public Future<Task> addGenTaskFutureToPool(Integer id, Future<Task> taskFuture) {
		return genTaskFutures.put(id, taskFuture);
	}

	public Task addToPool(Task task) {
		Task addedTask = tasks.put(task.getId(), task);
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
	public Integer addSendTaskCount(int taskId, int count) {
		return sendTaskCount.put(taskId, count);
	}

	@Override
	public Integer decreaseSendTaskCount(int taskId, int decrease) {
		Integer count = sendTaskCount.get(taskId);
		if (count == null) {
			return null;
		} else if (count <= 0) {
			removeTaskById(taskId);
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
	public List<Task> getWaitingTasks() {
		return getFromIterator(getNewTasksQueue().iterator());
	}

	public List<Task> getGeneratedTasks() {
		return getFromIterator(getGeneratedTasksQueue().iterator());
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
	public BlockingBoundedMap<Integer, Task> getGeneratingTasks() {
		return generatingTasks;
	}

	@Override
	public BlockingBoundedMap<Pair<Integer, Destination>, SendTask> getSendingSendTasks() {
		return sendingSendTasks;
	}

	@Override
	public Future<Task> getTaskFutureById(int id) {
		return genTaskFutures.get(id);
	}

	@Override
	public Task getTaskById(int id) {
		return tasks.get(id);
	}

	private boolean removeTaskById(int id) {
		Task task = tasks.get(id);
		return removeTask(task);
	}

	@Override
	public boolean removeTask(Task task) {
		int id = task.getId();
		boolean removed = tasks.remove(id, task);
		Future<Task> taskFuture = genTaskFutures.get(id);
		if (taskFuture != null) {
			taskFuture.cancel(true);
		}
		if (removed) {
			cancelSendTasks(id);
			sendTaskCount.remove(id);
		}
		return removed;
	}

	public Future<SendTask> removeSendTaskFuture(int taskId, Destination destination) {
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

	@Override
	public void modifyTask(Task task, List<Destination> destinations, boolean propagationForced) {
		//TODO: Do we need to synchronize here? We may change destinations just as they are being transformed into SendTasks.
		if (newTasksQueue.contains(task) || generatedTasksQueue.contains(task)) {
			task.setDestinations(destinations);
			task.setPropagationForced(propagationForced);
		} else {
			//TODO: Handle tasks that are currently being Generated/Sent
			throw new NotImplementedException();
		}
	}

	private void handleInterruptedException(Task task, InterruptedException e) {
		String errorMessage = "Thread was interrupted while tryinng to put Task " + task + " into new Tasks queue.";
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
