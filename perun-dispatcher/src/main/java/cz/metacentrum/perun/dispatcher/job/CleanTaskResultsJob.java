package cz.metacentrum.perun.dispatcher.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.metacentrum.perun.dispatcher.service.DispatcherManager;

/**
 * Cleans TaskResults from DB (older than 3 days, always keeps at least one for destination/service).
 *
 * @author Michal Karm Babacek
 */
@org.springframework.stereotype.Service(value = "cleanTaskResultsJob")
public class CleanTaskResultsJob {

	private final static Logger log = LoggerFactory.getLogger(CleanTaskResultsJob.class);

	private DispatcherManager dispatcherManager;
	private boolean enabled = true;

	// ------ setters -------------------------------

	public DispatcherManager getDispatcherManager() {
		return dispatcherManager;
	}

	@Autowired
	public void setDispatcherManager(DispatcherManager dispatcherManager) {
		this.dispatcherManager = dispatcherManager;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	// ------ methods -------------------------------

	/**
	 * If job is enabled, start it. This method is called periodically by spring task scheduler.
	 */
	public void doTheJob() {
		if (enabled) {
			log.debug("Entering CleanTaskResultsJob...");
			dispatcherManager.cleanOldTaskResults();
			log.debug("CleanTaskResultsJob done.");
		}
	}

}
