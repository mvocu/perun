package cz.metacentrum.perun.dispatcher.service;

import cz.metacentrum.perun.dispatcher.exceptions.PerunHornetQServerException;

/**
 * Main class for manging various parts of Dispatcher (hornetQ server, auditer listener, task scheduling, ...).
 *
 * @author Michal Karm Babacek
 */
public interface DispatcherManager {

	// /HornetQ server///
	void startPerunHornetQServer();

	void stopPerunHornetQServer();

	// /Prefetch rules and Dispatcher queues///
	void prefetchRulesAndDispatcherQueues() throws PerunHornetQServerException;

	// /System Queue Processor///
	void startProcessingSystemMessages();

	void stopProcessingSystemMessages();

	// /Parsing data///
	void startAuditerListener();

	void stopAuditerListener();

	// /Event Processor///
	void startProcessingEvents();

	void stopProcessingEvents();

	// /Task database///
	void loadSchedulingPool();

	void cleanOldTaskResults();

	void startSchedulingTasks();
}
