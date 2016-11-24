package cz.metacentrum.perun.engine.scheduling;

import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.taskslib.model.SendTask;
import cz.metacentrum.perun.taskslib.model.Task;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;

/**
 * This class groups all Task queues from Engine, providing means to add new Tasks, cancel/remove present ones, etc.
 */
public interface SchedulingPool {

	int getSize();

	Task addToPool(Task task);

	Future<Task> addGenTaskFutureToPool(Integer id, Future<Task> taskFuture);

	Integer addSendTaskCount(int taskId, int count);

	Integer decreaseSendTaskCount(int taskId, int decrease);

	Collection<Task> getPlannedTasks();

	Collection<Task> getGeneratingTasks();

	Collection<Task> getGeneratedTasks();

	BlockingDeque<Task> getNewTasksQueue();

	BlockingDeque<Task> getGeneratedTasksQueue();

	BlockingBoundedMap<Integer, Task> getGeneratingTasksBlockingMap();

	BlockingBoundedMap<Pair<Integer, Destination>, SendTask> getSendingSendTasksBlockingMap();

	ConcurrentMap<Integer, Future<Task>> getGenTaskFuturesMap();

	Future<Task> getGenTaskFutureById(int id);

	Task getTaskById(int id);

	Task removeTask(Task task);

	Task removeTask(int id);

	Future<SendTask> removeSendTaskFuture(int taskId, Destination destination);

	void modifyTask(Task task, List<Destination> destinations, boolean propagationForced);
}
