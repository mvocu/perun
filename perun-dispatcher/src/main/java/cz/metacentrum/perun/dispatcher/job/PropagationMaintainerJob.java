package cz.metacentrum.perun.dispatcher.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.metacentrum.perun.dispatcher.scheduling.PropagationMaintainer;

/**
 * Perform checking on finished tasks, reschedules error tasks, stuck tasks and old done tasks.
 *
 * @author Michal Karm Babacek
 */
@org.springframework.stereotype.Service(value = "propagationMaintainerJob")
public class PropagationMaintainerJob {

	private final static Logger log = LoggerFactory.getLogger(PropagationMaintainerJob.class);

	private PropagationMaintainer propagationMaintainer;
	private boolean enabled = true;

	// ------ setters -------------------------------

	public PropagationMaintainer getPropagationMaintainer() {
		return propagationMaintainer;
	}

	@Autowired
	public void setPropagationMaintainer(PropagationMaintainer propagationMaintainer) {
		this.propagationMaintainer = propagationMaintainer;
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
			log.info("Entering PropagationMaintainerJob: propagationMaintainer.checkResults().");
			propagationMaintainer.checkResults();
			log.info("PropagationMaintainerJob done: propagationMaintainer.checkResults() has completed.");
		}
	}

}
