package cz.metacentrum.perun.taskslib.model;


import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Pair;
import sun.security.krb5.internal.crypto.Des;

import java.io.Serializable;
import java.util.Date;

public class SendTask implements Serializable{
	private static final long serialVersionUID = 4795659061486919871L;

	private Pair<Integer, Destination> id;
	private SendTaskStatus status;
	private Date startTime;
	private Date endTime;
	private Task task;
	private Destination destination;
	private Integer returnCode;
	private String stdout;
	private String stderr;

	public SendTask(Task task, Destination destination) {
		this.task = task;
		this.destination = destination;
		setId();
	}

	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	public Task getTask() {
		return task;
	}

	public void setTask(Task task) {
		this.task = task;
	}

	public Destination getDestination() {
		return destination;
	}

	public void setDestination(Destination destination) {
		this.destination = destination;
	}

	public SendTaskStatus getStatus() {
		return status;
	}

	public void setStatus(SendTaskStatus status) {
		this.status = status;
	}

	public Integer getReturnCode() {
		return returnCode;
	}

	public void setReturnCode(Integer returnCode) {
		this.returnCode = returnCode;
	}

	public String getStdout() {
		return stdout;
	}

	public void setStdout(String stdout) {
		this.stdout = stdout;
	}

	public String getStderr() {
		return stderr;
	}

	public void setStderr(String stderr) {
		this.stderr = stderr;
	}

	public void setId() {
		id = new Pair<>(task.getId(), destination);
	}

	public Pair<Integer, Destination> getId() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		SendTask sendTask = (SendTask) o;

		if (startTime != null ? !startTime.equals(sendTask.startTime) : sendTask.startTime != null) return false;
		if (endTime != null ? !endTime.equals(sendTask.endTime) : sendTask.endTime != null) return false;
		if (!task.equals(sendTask.task)) return false;
		return destination.equals(sendTask.destination);

	}

	@Override
	public int hashCode() {
		int result = startTime != null ? startTime.hashCode() : 0;
		result = 31 * result + (endTime != null ? endTime.hashCode() : 0);
		result = 31 * result + task.hashCode();
		result = 31 * result + destination.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "SendTask{" +
				"status=" + status +
				", startTime=" + startTime +
				", endTime=" + endTime +
				", task=" + task +
				", destination=" + destination +
				", returnCode=" + returnCode +
				", stdout='" + stdout + '\'' +
				", stderr='" + stderr + '\'' +
				'}';
	}

	public static enum SendTaskStatus {
		SENDING, SENT, ERROR
	}
}
