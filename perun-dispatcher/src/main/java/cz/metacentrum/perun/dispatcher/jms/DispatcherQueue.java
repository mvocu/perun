package cz.metacentrum.perun.dispatcher.jms;

import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.hornetq.api.jms.HornetQJMSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

/**
 * Instance of JMS queue for sending messages to engine.
 * For each engine own queue is created, and stored in DispatcherQueuePool.
 *
 * @see cz.metacentrum.perun.dispatcher.jms.DispatcherQueuePool
 *
 * @author Michal Karm Babacek
 * @author Michal Voců
 * @author David Šarman
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
public class DispatcherQueue {

	private final static Logger log = LoggerFactory.getLogger(DispatcherQueue.class);

	private Queue queue;
	private Session session;
	private MessageProducer producer;
	private int clientID;
	private String queueName;

	// this one is to allow for mock objects which extend this class
	public DispatcherQueue(int clientID, String queueName) {
		this.clientID = clientID;
		this.queueName = queueName;
	}

	public DispatcherQueue(int clientID, String queueName, Session session) {
		this.clientID = clientID;
		this.queueName = queueName;
		this.session = session;
		try {
			// Step 1. Directly instantiate the JMS Queue object.
			queue = HornetQJMSClient.createQueue(queueName);
			if (log.isDebugEnabled()) {
				log.debug("Created queue named as: " + queueName);
			}
			// Step 6. Create a JMS Message Producer
			producer = session.createProducer(queue);
			if (log.isDebugEnabled()) {
				log.debug("Session created: " + session);
			}

		} catch (JMSException e) {
			log.error(e.toString(), e);
		} catch (Exception e) {
			log.error(e.toString(), e);
			// TODO: Restart connection...?
		}
	}

	/**
	 * Send JMS message to the Engine associated with this queue.
	 * This method does not block. It is going to be executed asynchronously.
	 *
	 * @param text Message content
	 */
	@Async
	public void sendMessage(String text) {

		try {
			// Step 7. Create a Text Message
			TextMessage message = session.createTextMessage("task|" + clientID + "|" + text);
			// Step 8. Send...
			producer.send(message);
			if (log.isDebugEnabled()) {
				log.debug("Sent message (queue:" + queueName + "): " + message.getText());
			}
		} catch (JMSException e) {
			log.error(e.toString(), e);
		} catch (Exception e) {
			log.error(e.toString(), e);
			// TODO: Restart connection...?
		}
	}

	/**
	 * Get ID of engine.
	 *
	 * @return ID of engine
	 */
	public int getClientID() {
		return clientID;
	}

	/**
	 * Get name of the queue for engine.
	 *
	 * @return Name of queue
	 */
	public String getQueueName() {
		return queueName;
	}

}
