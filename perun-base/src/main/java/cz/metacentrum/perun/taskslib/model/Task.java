package cz.metacentrum.perun.taskslib.model;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Facility;

/**
 *
 * @author Michal Karm Babacek
 */
public class Task implements Serializable {

	private static final long serialVersionUID = -1809998673612582742L;

	public enum TaskStatus {
		WAITING, PLANNED, GENERATING, GENERROR, GENERATED, SENDING, DONE, SENDERROR, ERROR
	}

	private int id;
	private int delay;
	private int recurrence;
	private Date startTime;
	private Date sentToEngine;
	private Date sendStartTime;
	private Date schedule;
	private Date genStartTime;
	private Date genEndTime;
	private Date sendEndTime;
	private Date endTime;
	private ExecService execService;
	private Facility facility;
	private List<Destination> destinations;
	private TaskStatus status;
	private boolean sourceUpdated;	
	private boolean propagationForced; 
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Task other = (Task) obj;
		if (!execService.equals(other.execService))
			return false;
		if (!facility.equals(other.facility))
			return false;
		if (id != other.id)
			return false;
		return true;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getDelay() {
		return delay;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

	public int getRecurrence() {
		return recurrence;
	}

	public void setRecurrence(int recurrence) {
		this.recurrence = recurrence;
	}

	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public Date getSchedule() {
		return schedule;
	}

	public void setSchedule(Date schedule) {
		this.schedule = schedule;
	}


	public int getExecServiceId() {
		if (execService != null) {
			return execService.getId();
		} else {
			return -1;
		}
	}

	public void setExecService(ExecService execService) {
		this.execService = execService;
	}

	public ExecService getExecService() {
		return execService;
	}

	public int getFacilityId() {
		if (facility != null) {
			return facility.getId();
		} else {
			return -1;
		}
	}

	public Date getGenEndTime() {
		return genEndTime;
	}

	public void setGenEndTime(Date genEndTime) {
		this.genEndTime = genEndTime;
	}

	public Date getSendEndTime() {
		return sendEndTime;
	}

	public void setSendEndTime(Date sendEndTime) {
		this.sendEndTime = sendEndTime;
	}

	public Date getSendStartTime() {
		return sendStartTime;
	}

	public Date getGenStartTime() {
		return genStartTime;
	}

	public void setGenStartTime(Date genStartTime) {
		this.genStartTime = genStartTime;
	}

	public Date getSentToEngine() {
		return sentToEngine;
	}

	public void setSentToEngine(Date sentToEngine) {
		this.sentToEngine = sentToEngine;
	}

	public void setSendStartTime(Date sendStartTime) {
		this.sendStartTime = sendStartTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	public Facility getFacility() {
		return facility;
	}

	public void setFacility(Facility facility) {
		this.facility = facility;
	}

	public List<Destination> getDestinations() {
		return destinations;
	}
	
	public void setDestinations(List<Destination> destinations) {
		this.destinations = destinations;
	}
	
	public TaskStatus getStatus() {
		return status;
	}

	public void setStatus(TaskStatus status) {
		this.status = status;
	}

	public String getBeanName(){
		return this.getClass().getSimpleName();
	}

	@Override
	public String toString() {
		return "Task [id=" + id +
				", delay=" + delay +
				", recurrence=" + recurrence +
				", startTime=" + startTime +
				", sendStartTime=" + sendStartTime +
				", schedule=" + schedule +
				", genEndTime=" + genEndTime +
				", sendEndTime=" + sendEndTime+
				", execService=" + execService +
				", facility=" + facility +
				", destinations=" + destinations +
				", status=" + status + "]";
	}

	public boolean isSourceUpdated() {
		return sourceUpdated;
	}

	public void setSourceUpdated(boolean sourceUpdated) {
		this.sourceUpdated = sourceUpdated;
	}

	public boolean isPropagationForced() {
		return propagationForced;
	}

	public void setPropagationForced(boolean propagationForced) {
		this.propagationForced = propagationForced;
	}
}
