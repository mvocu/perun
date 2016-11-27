package cz.metacentrum.perun.engine.scheduling.impl;

import cz.metacentrum.perun.engine.exceptions.TaskExecutionException;
import cz.metacentrum.perun.engine.scheduling.GenWorker;
import cz.metacentrum.perun.taskslib.model.ExecService;
import cz.metacentrum.perun.taskslib.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;

import static cz.metacentrum.perun.taskslib.model.Task.TaskStatus.GENERATED;
import static cz.metacentrum.perun.taskslib.model.Task.TaskStatus.GENERROR;

@Component("genWorker")
@Scope(value = "prototype")
public class GenWorkerImpl extends AbstractWorker implements GenWorker {
	private final static Logger log = LoggerFactory.getLogger(GenWorkerImpl.class);
	private Task task;

	public GenWorkerImpl(Task task) {
		this.task = task;
	}

	@Override
	public Task call() throws TaskExecutionException {
		ExecService execService = getTask().getExecService();
		log.info("EXECUTING GEN(worker:{}): Task ID:{}, Facility ID:{}",
				new Object[]{hashCode(), getTask().getId(), getTask().getFacilityId()});

		ProcessBuilder pb = new ProcessBuilder(execService.getScript(), "-f", String.valueOf(getTask().getFacilityId()));

		try {
			super.execute(pb);

			getTask().setGenEndTime(new Date(System.currentTimeMillis()));
			if (getReturnCode() != 0) {
				log.error("GEN task failed. Ret code {}, STDOUT: {}, STDERR: {}, Task ID: {}",
						new Object[]{getReturnCode(), getStdout(), getStderr(), getTask().getId()});
				getTask().setStatus(GENERROR);
				getTask().setGenEndTime(new Date(System.currentTimeMillis()));
				throw new TaskExecutionException(task.getId(), getReturnCode(), getStdout(), getStderr());
			} else {
				getTask().setStatus(GENERATED);
				log.info("GEN task finished. Ret code {}, STDOUT: {}, STDERR: {}, Task ID: {}",
						new Object[]{getReturnCode(), getStdout(), getStderr(), getTask()});
				return getTask();
			}
		} catch (IOException e) {
			log.error(e.toString(), e);
			getTask().setStatus(GENERROR);
			getTask().setGenEndTime(new Date(System.currentTimeMillis()));
			throw new TaskExecutionException(task.getId(), e);
		} catch (InterruptedException e) {
			log.warn("Tasks {} execution interrupted.", task, e);
			task.setStatus(GENERROR);
			throw new TaskExecutionException(task.getId(), e);
		}
	}

	@Override
	public Integer getId() {
		return task.getId();
	}

	public Task getTask() {
		return task;
	}
}
