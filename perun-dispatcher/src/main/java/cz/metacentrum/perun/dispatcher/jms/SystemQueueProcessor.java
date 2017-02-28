package cz.metacentrum.perun.dispatcher.jms;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.hornetq.core.remoting.impl.netty.TransportConstants;

import cz.metacentrum.perun.dispatcher.exceptions.MessageFormatException;
import cz.metacentrum.perun.dispatcher.exceptions.PerunHornetQServerException;
import cz.metacentrum.perun.dispatcher.hornetq.PerunHornetQServer;
import cz.metacentrum.perun.dispatcher.processing.SmartMatcher;
import cz.metacentrum.perun.dispatcher.scheduling.PropagationMaintainer;

/**
 * Main class ensuring processing of JMS communication between Dispatcher and Engines.
 * It start/stop processing of messages, create queues and load processing rules for Engines.
 * Also provide method for message parsing.
 *
 * Queues to Engines are represented by DispatcherQueue objects. Queue is used by TaskScheduler.
 * Queue from Engine is represented by SystemQueueReceiver. Received messages result in calls to PropagationMaintainer.
 *
 * @see cz.metacentrum.perun.dispatcher.jms.DispatcherQueue
 * @see cz.metacentrum.perun.dispatcher.scheduling.TaskScheduler
 * @see cz.metacentrum.perun.dispatcher.jms.SystemQueueReceiver
 * @see cz.metacentrum.perun.dispatcher.scheduling.PropagationMaintainer
 *
 * @author Michal Karm Babacek
 * @author Michal Voců
 * @author David Šarman
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
@org.springframework.stereotype.Service(value = "systemQueueProcessor")
public class SystemQueueProcessor {

	private final static Logger log = LoggerFactory.getLogger(SystemQueueProcessor.class);

	private Properties dispatcherProperties;
	private DispatcherQueuePool dispatcherQueuePool;
	private PerunHornetQServer perunHornetQServer;
	private SmartMatcher smartMatcher;
	private TaskExecutor taskExecutor;
	private SystemQueueReceiver systemQueueReceiver;
	private PropagationMaintainer propagationMaintainer;

	private Session session = null;
	private boolean processingMessages = false;
	private boolean systemQueueInitiated = false;
	private ConnectionFactory cf;
	private Connection connection;


	// ----- setters -------------------------------------


	public Properties getDispatcherProperties() {
		return dispatcherProperties;
	}

	@Autowired
	public void setDispatcherProperties(Properties dispatcherProperties) {
		this.dispatcherProperties = dispatcherProperties;
	}

	public DispatcherQueuePool getDispatcherQueuePool() {
		return dispatcherQueuePool;
	}

	@Autowired
	public void setDispatcherQueuePool(DispatcherQueuePool dispatcherQueuePool) {
		this.dispatcherQueuePool = dispatcherQueuePool;
	}

	public PerunHornetQServer getPerunHornetQServer() {
		return perunHornetQServer;
	}

	@Autowired
	public void setPerunHornetQServer(PerunHornetQServer perunHornetQServer) {
		this.perunHornetQServer = perunHornetQServer;
	}

	public SmartMatcher getSmartMatcher() {
		return smartMatcher;
	}

	@Autowired
	public void setSmartMatcher(SmartMatcher smartMatcher) {
		this.smartMatcher = smartMatcher;
	}

	public TaskExecutor getTaskExecutor() {
		return taskExecutor;
	}

	@Autowired
	public void setTaskExecutor(TaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	public SystemQueueReceiver getSystemQueueReceiver() {
		return systemQueueReceiver;
	}

	@Autowired
	public void setSystemQueueReceiver(SystemQueueReceiver systemQueueReceiver) {
		this.systemQueueReceiver = systemQueueReceiver;
	}

	public PropagationMaintainer getPropagationMaintainer() {
		return propagationMaintainer;
	}

	@Autowired
	public void setPropagationMaintainer(PropagationMaintainer propagationMaintainer) {
		this.propagationMaintainer = propagationMaintainer;
	}


	// ----- methods -------------------------------------


	/**
	 * Setup JMS queues between dispatcher and engines and start processing available messages.
	 * HornetQ server must be running already.
	 *
	 * @see cz.metacentrum.perun.dispatcher.hornetq.PerunHornetQServer
	 */
	public void startProcessingSystemMessages() {

		connection = null;
		try {
			// Step 2. Instantiate the TransportConfiguration object which
			// contains the knowledge of what transport to use,
			// The server port etc.
			if (log.isDebugEnabled()) {
				log.debug("Creating transport configuration...");
				log.debug("Gonna connect to the host["
						+ dispatcherProperties.getProperty("dispatcher.ip.address")
						+ "] on port["
						+ dispatcherProperties.getProperty("dispatcher.port")
						+ "]...");
			}
			Map<String, Object> connectionParams = new HashMap<String, Object>();
			connectionParams.put(TransportConstants.PORT_PROP_NAME, Integer.parseInt(dispatcherProperties.getProperty("dispatcher.port")));
			connectionParams.put(TransportConstants.HOST_PROP_NAME, dispatcherProperties.getProperty("dispatcher.ip.address"));

			TransportConfiguration transportConfiguration = new TransportConfiguration(NettyConnectorFactory.class.getName(), connectionParams);

			// Step 3 Directly instantiate the JMS ConnectionFactory object
			// using that TransportConfiguration
			log.debug("Creating connection factory...");
			cf = (ConnectionFactory) HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, transportConfiguration);
			((HornetQConnectionFactory)cf).setUseGlobalPools(false);

			// Step 4.Create a JMS Connection
			log.debug("Creating connection...");
			connection = cf.createConnection();

			// Step 5. Create a JMS Session
			log.debug("Creating session...");
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

			// Step 10. Start the Connection
			log.debug("Starting connection...");
			connection.start();
			if (processingMessages) {
				// make sure processing is stopped before new start when called as "restart"
				systemQueueReceiver.stop();
			}
			systemQueueReceiver.setUp("systemQueue", session);
			log.debug("Receiving messages started...");
			taskExecutor.execute(systemQueueReceiver);
			log.debug("JMS Initialization done.");
			processingMessages = true;

		} catch (JMSException e) {
			// If unable to connect to the server...
			log.error("Connection failed. \nThis is weird...are you sure that the Perun-Dispatcher is running on host ["
							+ dispatcherProperties.getProperty("dispatcher.ip.address")
							+ "] on port [" + dispatcherProperties.getProperty("dispatcher.port")
							+ "] ? \nSee: perun-dispatcher.properties. We gonna wait 5 sec and try again...", e);
			throw new RuntimeException(e);
		} catch (Exception e) {
			log.error("Can't start processing of JMS: {}", e);
		}
	}

	/**
	 * Stop processing of JMS messages between dispatcher and engines and close the connection.
	 */
	public void stopProcessingSystemMessages() {
		if (processingMessages && systemQueueReceiver != null) {
			systemQueueReceiver.stop();
			try {
				connection.stop();
				connection.close();
				((HornetQConnectionFactory)cf).close();
				log.debug("JMS processing stopped.");
			} catch (JMSException e) {
				log.error("Error closing JMS client connection: ", e.toString());
			}
		}
	}

	/**
	 * TRUE if processing of JMS messages running
	 *
	 * @return TRUE processing of JMS is running / FALSE otherwise
	 */
	public boolean isProcessingMessages() {
		return processingMessages;
	}

	/**
	 * TRUE if JMS queue between dispatcher and engine is initialized.
	 *
	 * @return TRUE if JMS queue is initialized / FALSE otherwise
	 */
	public boolean isSystemQueueInitiated() {
		return systemQueueInitiated;
	}

	/**
	 * Process content of JMS message received from Engine. This is called by SystemQueueReceiver
	 * for each message.
	 *
	 * Expected message format is:
	 *
	 * Register engine message
	 * register:x
	 * where x is an Integer that represents Engine's ID in the Perun DB.
	 *
	 * Good bye engine message
	 * goodbye:x
	 * where x is an Integer that represents Engine's ID in the Perun DB.
	 *
	 * Task status change message
	 * task:x:y:status:timestamp
	 * where x is an Integer that represents Engine's ID in the Perun
	 * y is an Integer that represents task ID
	 * status is string representation of task status
	 * timestamp is a string representation of timestamp (long)
	 *
	 * Task result message
	 * taskresult:x:object
	 * where x is an Integer that represents Engine's ID in the Perun
	 * object is serialized TaskResult object sent from Engine
	 *
	 * @see cz.metacentrum.perun.dispatcher.jms.SystemQueueReceiver
	 *
	 * @param message Message to be parsed a processed
	 * @throws PerunHornetQServerException When HornetQ server is not running
	 * @throws MessageFormatException When Engine sent malformed JMS message
	 */
	protected void processDispatcherQueueAndMatchingRule(String message) throws PerunHornetQServerException, MessageFormatException {

		if (perunHornetQServer.isServerRunning() && perunHornetQServer.getJMSServerManager() != null) {

			log.debug("Processing JMS message: " + message);

			if (null == message || message.isEmpty()) {
				throw new MessageFormatException("Engine sent empty message");
			}

			String[] clientIDsplitter = message.split(":", 3);
			if(clientIDsplitter.length < 2) {
				throw new MessageFormatException("Engine sent a malformed message, not enough params [" + message + "]");
			}

			int clientID = 0;
			try {
				clientID = Integer.parseInt(clientIDsplitter[1]);
			} catch (NumberFormatException e) {
				throw new MessageFormatException("Engine sent a malformed message, can't parse ID of engine [" + message + "]", e);
			}

			// process expected messages

			if (clientIDsplitter[0].equalsIgnoreCase("register")) {

				// Do we have this queue already?
				DispatcherQueue dispatcherQueue = dispatcherQueuePool.getDispatcherQueueByClient(clientID);
				if (dispatcherQueue != null) {
					// Yes, so we just reload matching rules...
					smartMatcher.reloadRulesFromDBForEngine(clientID);
					// ...and close all tasks that could have been running there
					propagationMaintainer.closeTasksForEngine(clientID);
				} else {
					// No, we have to create the whole JMS queue and load matching rules...
					createDispatcherQueueForClient(clientID);
				}

			} else if (clientIDsplitter[0].equalsIgnoreCase("goodbye")) {

				// engine is going down, should mark all tasks as failed
				propagationMaintainer.closeTasksForEngine(clientID);
				dispatcherQueuePool.removeDispatcherQueue(clientID);

			} else if (clientIDsplitter[0].equalsIgnoreCase("task")) {

				clientIDsplitter = message.split(":", 5);

				if(clientIDsplitter.length < 5) {
					throw new MessageFormatException("Engine sent a malformed message, not enough params [" + message + "]");
				}

				propagationMaintainer.onTaskStatusChange(
						Integer.parseInt(clientIDsplitter[2]),
						clientIDsplitter[3],
						clientIDsplitter[4]);

			} else if (clientIDsplitter[0].equalsIgnoreCase("taskresult")) {

				clientIDsplitter = message.split(":", 3);
				if(clientIDsplitter.length < 3) {
					throw new MessageFormatException("Engine sent a malformed message, not enough params [" + message + "]");
				}

				propagationMaintainer.onTaskDestinationComplete(clientID, clientIDsplitter[2]);

			} else {
				throw new MessageFormatException("Engine sent a malformed message, unknown type of message [" + message + "]");
			}

		} else {
			throw new PerunHornetQServerException("HornetQ server is not running or JMSServerManager is fucked up...");
		}
	}

	/**
	 * Create JMS queues for all engines (ID passed).
	 *
	 * @param clientIDs IDs of Engines
	 * @throws PerunHornetQServerException if HornetQ server is not running
	 */
	public void createDispatcherQueuesForClients(Set<Integer> clientIDs) throws PerunHornetQServerException {
		if (perunHornetQServer.isServerRunning() && perunHornetQServer.getJMSServerManager() != null) {
			for (Integer clientID : clientIDs) {
				createDispatcherQueueForClient(clientID);
			}
		} else {
			throw new PerunHornetQServerException("HornetQ server is not running or JMSServerManager is fucked up...");
		}
	}

	/**
	 * Create JMS queue for Engine specified by its ID.
	 *
	 * @param clientID ID of Engine
	 */
	private void createDispatcherQueueForClient(Integer clientID) {

		String queueName = "queue" + clientID;

		try {
			perunHornetQServer.getJMSServerManager().createQueue(false, queueName, null, false);
		} catch (Exception e) {
			log.error("Can't create JMS {}: {}", queueName, e);
		}

		DispatcherQueue dispatcherQueue = new DispatcherQueue(clientID, queueName, session);
		// Rules
		smartMatcher.reloadRulesFromDBForEngine(clientID);
		// Add to the queue
		dispatcherQueuePool.addDispatcherQueue(dispatcherQueue);
	}

}
