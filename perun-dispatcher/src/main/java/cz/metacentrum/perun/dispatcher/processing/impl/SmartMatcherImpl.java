package cz.metacentrum.perun.dispatcher.processing.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import cz.metacentrum.perun.dispatcher.dao.RulesDao;
import cz.metacentrum.perun.dispatcher.jms.DispatcherQueue;
import cz.metacentrum.perun.dispatcher.model.Event;
import cz.metacentrum.perun.dispatcher.model.MatchingRule;
import cz.metacentrum.perun.dispatcher.processing.SmartMatcher;

/**
 * 
 * @author Michal Karm Babacek
 */
@org.springframework.stereotype.Service(value = "smartMatcher")
public class SmartMatcherImpl implements SmartMatcher {

	private final static Logger log = LoggerFactory
			.getLogger(SmartMatcherImpl.class);

	@Autowired
	private RulesDao rulesDao;
	private ConcurrentMap<Integer, MatchingRule> matchingRules = new ConcurrentHashMap<Integer, MatchingRule>();

	@Override
	public boolean doesItMatch(Event event, DispatcherQueue dispatcherQueue) {
		MatchingRule matchingRule = matchingRules.get(dispatcherQueue.getClientID());
		if (matchingRule == null) {
			log.debug("MATCHER rules({}): Doesn't match. No such rule.", matchingRules.size());
			return true;
		}
		for (String rule : matchingRule.getRules()) {
			if (event.getHeader().contains(rule)) {
				log.debug("MATCHER rules({}): Header [{}] contains [{}]",
						new Object[] {matchingRules.size(), event.getHeader(), rule});
				return true;
			}
			log.debug("MATCHER rules({}): Doesn't match: [{}] doesn't contain [{}]",
					new Object[] {matchingRules.size(), event.getHeader(), rule});
		}
		return true;
	}

	@Override
	@Async
	@Transactional
	public void loadAllRulesFromDB() {
		synchronized (this) {
			Map<Integer, List<String>> clientIDandRules = rulesDao
					.loadRoutingRules();
			for (Integer clientID : clientIDandRules.keySet()) {
				MatchingRule matchingRule = new MatchingRule(
						clientIDandRules.get(clientID));
				matchingRules.put(clientID, matchingRule);
			}
		}
	}

	@Override
	@Async
	@Transactional
	public void reloadRulesFromDBForEngine(Integer clientID) {
		synchronized (this) {
			matchingRules.remove(clientID);
			MatchingRule matchingRule = new MatchingRule(
					rulesDao.loadRoutingRulesForEngine(clientID));
			matchingRules.put(clientID, matchingRule);
		}
	}

	@Override
	public Set<Integer> getClientsWeHaveRulesFor() {
		return matchingRules.keySet();
	}

}
