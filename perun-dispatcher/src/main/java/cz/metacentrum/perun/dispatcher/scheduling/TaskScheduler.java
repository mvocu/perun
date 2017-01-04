package cz.metacentrum.perun.dispatcher.scheduling;

import cz.metacentrum.perun.dispatcher.scheduling.impl.TaskScheduled;
import cz.metacentrum.perun.taskslib.model.Task;
import cz.metacentrum.perun.taskslib.runners.Runner;

public interface TaskScheduler extends Runner {

	TaskScheduled scheduleTask(Task task);

	void setSchedulingPool(SchedulingPool schedulingPool);
}
