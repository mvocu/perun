package cz.metacentrum.perun.engine.scheduling;

import cz.metacentrum.perun.engine.jms.JMSQueueManager;

/**
 *
 * @author Michal Karm Babacek JavaDoc coming soon...
 *
 */
public interface PropagationMaintainer {

	void endStuckTasks();

	void setJmsQueueManager(JMSQueueManager jmsQueueManager);
}
