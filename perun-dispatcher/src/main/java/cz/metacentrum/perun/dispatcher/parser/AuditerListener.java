package cz.metacentrum.perun.dispatcher.parser;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.impl.AuditerConsumer;
import cz.metacentrum.perun.dispatcher.model.Event;
import cz.metacentrum.perun.dispatcher.processing.EventQueue;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service(value = "auditerListener")
public class AuditerListener {

	private final static Logger log = LoggerFactory.getLogger(AuditerListener.class);

	private AuditerConsumer auditerConsumer;
	private String dispatcherName;
	private boolean running = true;
	boolean whichOfTwoRules = false;

	@Autowired private EventQueue eventQueue;
	@Autowired private DataSource dataSource;
	@Autowired private Properties dispatcherProperties;

	// ----- setters -------------------------------------

	public EventQueue getEventQueue() {
		return eventQueue;
	}

	public void setEventQueue(EventQueue eventQueue) {
		this.eventQueue = eventQueue;
	}

	public DataSource getDataSource() {
		return dataSource;
	}

	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	public Properties getDispatcherProperties() {
		return dispatcherProperties;
	}

	public void setDispatcherProperties(Properties dispatcherProperties) {
		this.dispatcherProperties = dispatcherProperties;
	}

	// ----- methods -------------------------------------

	public void init() {

		dispatcherName = dispatcherProperties.getProperty("dispatcher.ip.address") + ":" + dispatcherProperties.getProperty("dispatcher.port");

		try {
			while(running) {
				try {
					this.auditerConsumer = new AuditerConsumer(dispatcherName, dataSource);
					while (running) {
						for (String message : auditerConsumer.getMessagesForParser()) {
							Event event = new Event();
							event.setTimeStamp(System.currentTimeMillis());
							if (whichOfTwoRules) {
								event.setHeader("portishead");
								whichOfTwoRules = false;
							} else {
								event.setHeader("clockworkorange");
								whichOfTwoRules = true;
							}
							event.setData(message);
							eventQueue.add(event);
						}
						Thread.sleep(1000);
					}
				} catch (InternalErrorException e) {
					log.error("Error in AuditerConsumer: " + e.getMessage() + ", trying to recover by getting a new one.");
					this.auditerConsumer = null;
				}
				Thread.sleep(10000);
			}
		} catch (InterruptedException e) {
			log.error("Error in AuditerLister: {}" + e);
			throw new RuntimeException("Somebody has interrupted us...", e);
		}
	}

}
