package cz.metacentrum.perun.dispatcher.integration;

import java.util.Properties;

import javax.annotation.PreDestroy;

import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.web.context.WebApplicationContext;

import cz.metacentrum.perun.dispatcher.exceptions.PerunHornetQServerException;
import cz.metacentrum.perun.dispatcher.main.DispatcherStarter;
import cz.metacentrum.perun.dispatcher.service.DispatcherManager;

public class DispatcherPerformanceTest {
	private final static Logger log = LoggerFactory.getLogger(DispatcherPerformanceTest.class);

	private DispatcherManager dispatcherManager;
	@Autowired
	private AbstractApplicationContext springCtx;
	@Autowired
	private Properties dispatcherPropertiesBean;
	@Autowired
	@Qualifier("perunScheduler")
	private SchedulerFactoryBean perunScheduler;
	
	/**
	 * Initialize integrated dispatcher.
	 */
	public final void init() {

		try {

			dispatcherManager = springCtx.getBean("dispatcherManager", DispatcherManager.class);
			if(springCtx instanceof WebApplicationContext) {
				// do nothing here
			} else {
				springCtx.registerShutdownHook();
			}
			// Register into the database
			// DO NOT: dispatcherStarter.dispatcherManager.registerDispatcher();
			// Start HornetQ server
			dispatcherManager.startPerunHornetQServer();
			// Start System Queue Processor
			dispatcherManager.startProcessingSystemMessages();
			// Prefetch rules for all the Engines in the Perun DB and create
			// Dispatcher queues
			dispatcherManager.prefetchRulesAndDispatcherQueues();
			// reload tasks from database
			// not for perftest: dispatcherManager.loadSchedulingPool();
			// Start parsers (mining data both from Grouper and PerunDB)
			// not for perftest: dispatcherManager.startParsingData();
			// Start Event Processor
			// not for perftest: dispatcherManager.startProcessingEvents();

			// populate task database
			
			// get current time -> start time
			
			// start propagation
			
			// wait for all propagations to complete
			
			// get current time -> end time
			
			// print results and (wait for) exit

		} catch (PerunHornetQServerException e) {
			log.error(e.toString(), e);
		} catch (Exception e) {
			log.error(e.toString(), e);
		}

	}

	@PreDestroy
	public void destroy() {
		try {
			// stop current scheduler
			perunScheduler.stop();
			// stop job triggers
			perunScheduler.getScheduler().pauseAll();
		} catch (SchedulerException ex) {
			log.error("Unable to stop dispatcher scheduler: {}", ex);
		}
		// stop currently running jobs
		dispatcherManager.stopProcessingEvents();
		dispatcherManager.stopParsingData();
		dispatcherManager.stopProcessingSystemMessages();
		dispatcherManager.stopPerunHornetQServer();
	}

}
