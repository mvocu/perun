package cz.metacentrum.perun.engine.service.impl;

import cz.metacentrum.perun.engine.runners.GenCollector;
import cz.metacentrum.perun.engine.runners.GenPlanner;
import cz.metacentrum.perun.engine.runners.SendCollector;
import cz.metacentrum.perun.engine.runners.SendPlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.metacentrum.perun.engine.jms.JMSQueueManager;
import cz.metacentrum.perun.engine.scheduling.SchedulingPool;
import cz.metacentrum.perun.engine.service.EngineManager;

/**
 * @author Michal Karm Babacek
 * @author Michal Voců
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
@org.springframework.stereotype.Service(value = "engineManager")
public class EngineManagerImpl implements EngineManager {

	private final static Logger log = LoggerFactory.getLogger(EngineManagerImpl.class);

	@Autowired
	private GenPlanner genPlanner;
	@Autowired
	private GenCollector genCollector;
	@Autowired
	private SendPlanner sendPlanner;
	@Autowired
	private SendCollector sendCollector;
	@Autowired
	private JMSQueueManager jmsQueueManager;
	@Autowired
	private SchedulingPool schedulingPool;

	public EngineManagerImpl() {
	}

	public EngineManagerImpl(JMSQueueManager jmsQueueManager, SchedulingPool schedulingPool) {
		this.jmsQueueManager = jmsQueueManager;
		this.schedulingPool = schedulingPool;
	}

	@Override
	public void startMessaging() {
		jmsQueueManager.start();
	}

	@Override
	public void startRunnerThreads() {
		new Thread(genPlanner, "genPlanner").start();
		new Thread(genCollector, "genCollector").start();
		new Thread(sendPlanner, "sendPlanner").start();
		new Thread(sendCollector, "sendCollector").start();
	}

	@Override
	public void stopRunnerThreads() {
		genPlanner.stop();
		genCollector.stop();
		sendPlanner.stop();
		sendCollector.stop();
	}


	public void setJmsQueueManager(JMSQueueManager jmsQueueManager) {
		this.jmsQueueManager = jmsQueueManager;
	}

	public JMSQueueManager getJmsQueueManager() {
		return jmsQueueManager;
	}

	public SchedulingPool getSchedulingPool() {
		return schedulingPool;
	}

	public void setSchedulingPool(SchedulingPool schedulingPool) {
		this.schedulingPool = schedulingPool;
	}


}
