package cz.metacentrum.perun.controller.model;

import cz.metacentrum.perun.core.api.Service;
import cz.metacentrum.perun.taskslib.model.ExecService;

/**
 * Extension of Service (from Perun-Core) to provide more info for GUI
 *
 * @author Pavel Zlámal <256627@mail.muni.cz>
 */
public class ServiceForGUI extends Service {

	// global service info calculated by GeneralServiceManager
	// (based on allowed status of exec services)
	private boolean allowedOnFacility;
	private ExecService execService;

	public ServiceForGUI(Service service){

		setId(service.getId());
		setName(service.getName());

	}

	public void setAllowedOnFacility(boolean allowed){
		allowedOnFacility = allowed;
	}

	public boolean getAllowedOnFacility(){
		return allowedOnFacility;
	}

	public void setExecService(ExecService send){
		this.execService = send;
	}

	public ExecService getExecService(){
		return this.execService;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ServiceForGUI other = (ServiceForGUI) obj;
		if (allowedOnFacility != other.allowedOnFacility)
			return false;
		if (execService != other.execService)
			return false;

		return true;
	}

	@Override
	public String toString() {
		return "ServiceForGUI [allowedOnFacility=" + allowedOnFacility
			+ ", execService = " + execService
			+ ", Service=" + super.toString() + "]";
	}

}
