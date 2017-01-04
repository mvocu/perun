package cz.metacentrum.perun.taskslib.service.impl;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.taskslib.exceptions.TaskStoreException;
import cz.metacentrum.perun.taskslib.service.TaskStore;
import cz.metacentrum.perun.taskslib.model.ExecService;
import cz.metacentrum.perun.taskslib.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskStoreImpl implements TaskStore {
	private final static Logger log = LoggerFactory.getLogger(TaskStoreImpl.class);
	private final Map<Integer, Task> tasksById = new HashMap<>();
	private final Map<Pair<Facility, ExecService>, Task> tasksByFacilityAndExecService = new HashMap<>();

	public TaskStoreImpl() {
	}

	private static Predicate<Task> getStatusPredicate(final Task.TaskStatus ... status) {
		return new Predicate<Task>() {
			@Override
			public boolean apply(Task task) {
				return Arrays.asList(status).contains(task.getStatus());
			}
		};
	}

	@Override
	public Task getTask(int id) {
		return tasksById.get(id);
	}

	@Override
	public Task getTask(Facility facility, ExecService execService) {
		return tasksByFacilityAndExecService.get(new Pair<>(facility, execService));
	}

	@Override
	public int getSize() {
		return tasksById.size();
	}

	@Override
	public Task addToPool(Task task) throws TaskStoreException {
		if (task.getExecService() == null) {
			log.error("Tried to insert Task {} with no ExecService", task);
			throw new IllegalArgumentException("Tasks ExecService not set.");
		} else if (task.getFacility() == null) {
			log.error("Tried to insert Task {} with no Facility", task);
			throw new IllegalArgumentException("Tasks Facility not set.");
		}
		Task idAdded = tasksById.put(task.getId(), task);
		Task otherAdded = tasksByFacilityAndExecService.put(
				new Pair<>(task.getFacility(), task.getExecService()), task);
		if (idAdded != otherAdded) {
			log.error("Task returned from both Maps after insert differ. taskById {}, taskByFacilityAndExecService {}", idAdded, otherAdded);
			throw new TaskStoreException("Tasks returned after insert into both Maps differ.");
		} else {
			return idAdded;
		}
	}

	@Override
	public List<Task> getAllTasks() {
		return null;
	}

	@Override
	public List<Task> getTasksWithStatus(Task.TaskStatus... status) {
		Collection<Task> tasks = tasksById.values();
		return new ArrayList<>(Collections2.filter(tasks, getStatusPredicate(status)));
	}

	@Override
	public Task removeTask(Task task) throws TaskStoreException {
		Task idRemoved = tasksById.remove(task.getId());
		Task otherRemoved = tasksByFacilityAndExecService.remove(new Pair<>(task.getFacility(), task.getExecService()));
		if (idRemoved != otherRemoved) {
			log.error("Inconsistent state occurred after removing Task {} from TaskStore", task);
			throw new TaskStoreException("Unable to remove Task properly.");
		}
		return idRemoved;
	}

	@Override
	public Task removeTask(int id) throws TaskStoreException {
		Task task = getTask(id);
		if (task != null) {
			task = removeTask(getTask(id));
		}
		return task;
	}

	@Override
	public void clear() {
		tasksById.clear();
		tasksByFacilityAndExecService.clear();
	}
}
