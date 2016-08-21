package cz.metacentrum.perun.engine.scheduling.impl;

import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.engine.exceptions.TaskExecutionException;
import cz.metacentrum.perun.engine.scheduling.SendWorker;
import cz.metacentrum.perun.taskslib.model.ExecService;
import cz.metacentrum.perun.taskslib.model.SendTask;
import cz.metacentrum.perun.taskslib.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

import static cz.metacentrum.perun.taskslib.model.SendTask.SendTaskStatus.ERROR;
import static cz.metacentrum.perun.taskslib.model.SendTask.SendTaskStatus.SENT;
import static cz.metacentrum.perun.taskslib.model.Task.TaskStatus.GENERROR;


public class SendWorkerImpl extends AbstractWorker implements SendWorker {
	private final static Logger log = LoggerFactory.getLogger(SendWorkerImpl.class);

	private Destination destination;
	private SendTask sendTask;
	private Pair<Integer, Destination> id;

	public SendWorkerImpl(Destination destination, SendTask sendTask) {
		this.destination = destination;
		this.sendTask = sendTask;

	}

	@Override
	public Destination getDestination() {
		return destination;
	}

	@Override
	public SendTask call() throws TaskExecutionException {
		Task task = sendTask.getTask();
		ExecService execService = task.getExecService();
		ProcessBuilder pb = new ProcessBuilder(execService.getScript(), task.getFacility().getName(), destination.getDestination(), destination.getType());

		try {
			super.execute(pb);

			sendTask.setStdout(super.getStdout());
			sendTask.setStderr(super.getStderr());
			sendTask.setReturnCode(super.getReturnCode());
			sendTask.setEndTime(new Date(System.currentTimeMillis()));

			if (getReturnCode() != 0) {
				log.info("SEND task failed. Ret code {}, STDOUT: {}, STDERR: {}, Task ID: {}",
						new Object[]{getReturnCode(), getStdout(), getStderr(), sendTask.getTask().getId()});
				sendTask.setStatus(ERROR);
				throw new TaskExecutionException(new Pair<>(task.getId(), sendTask.getDestination()), getReturnCode(), getStdout(), getStderr());
			} else {
				sendTask.setStatus(SENT);
				return sendTask;
			}

		} catch (IOException e) {
			String errorMsg = "IOException occured when sending SendTask " + sendTask;
			log.warn(errorMsg, e);
			sendTask.setStatus(ERROR);
			throw new TaskExecutionException(new Pair<>(task.getId(), sendTask.getDestination()), e);
		} catch (InterruptedException e) {
			log.warn("SendTasks {} execution interrupted.", sendTask, e);
			task.setStatus(GENERROR);
			throw new TaskExecutionException(new Pair<>(task.getId(), sendTask.getDestination()), e);
		}
	}

	@Override
	public Pair<Integer, Destination> getId() {
		return id;
	}

	public SendTask getSendTask() {
		return sendTask;
	}
}
