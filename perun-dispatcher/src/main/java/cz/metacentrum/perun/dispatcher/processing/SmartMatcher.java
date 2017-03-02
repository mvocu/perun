package cz.metacentrum.perun.dispatcher.processing;

import java.util.Set;

import cz.metacentrum.perun.dispatcher.jms.EngineMessageProducer;
import cz.metacentrum.perun.dispatcher.model.Event;

/**
 *
 * @author Michal Karm Babacek JavaDoc coming soon...
 *
 */
public interface SmartMatcher {

	boolean doesItMatch(Event event, EngineMessageProducer engineMessageProducer);

	void loadAllRulesFromDB();

	void reloadRulesFromDBForEngine(Integer clientID);

	Set<Integer> getClientsWeHaveRulesFor();
}
