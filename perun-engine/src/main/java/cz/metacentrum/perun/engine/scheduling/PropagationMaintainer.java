package cz.metacentrum.perun.engine.scheduling;

import cz.metacentrum.perun.engine.jms.JMSQueueManager;

/**
 * Implements logic needed to end Tasks that get stuck in Engine for too long.
 * @author Michal Karm Babacek
 */
public interface PropagationMaintainer {

	void endStuckTasks();

	void setJmsQueueManager(JMSQueueManager jmsQueueManager);
}
