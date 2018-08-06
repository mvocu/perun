package cz.metacentrum.perun.ldapc.processor.impl;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.metacentrum.perun.auditparser.AuditParser;
import cz.metacentrum.perun.core.api.AuditMessage;
import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.impl.AuditerConsumer;
import cz.metacentrum.perun.ldapc.beans.LdapProperties;
import cz.metacentrum.perun.ldapc.processor.EventDispatcher;
import cz.metacentrum.perun.ldapc.processor.EventProcessor;
import cz.metacentrum.perun.ldapc.service.LdapcManager;
import cz.metacentrum.perun.rpclib.Rpc;

public class EventDispatcherImpl implements EventDispatcher, Runnable {

	private final static Logger log = LoggerFactory.getLogger(EventDispatcherImpl.class);

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

	@Autowired
	private LdapProperties ldapProperties;
	@Autowired
	private LdapcManager ldapcManager;

	private boolean running = false;

	private List<Pair<DispatchEventCondition, EventProcessor>> registeredProcessors;

	private class MessageBeansImpl implements MessageBeans {

		@Override
		public void addBean(PerunBean p) {
			
		}
		
	}
	
	@Override
	public void run() {

		if(!ldapProperties.propsLoaded()) throw new RuntimeException("LdapcProperties is not autowired correctly!");

		//Get instance of auditerConsumer and set running to true

		running = true;
		Integer lastProcessedIdNumber = 0;
		AuditMessage message = new AuditMessage(0, "Empty", null, null, null);
		List<AuditMessage> messages;

		try {
			//If running is true, then this process will be continuously
			while (running) {

				messages = null;
				int sleepTime = 1000;
				//Waiting for new messages. If consumer failed in some internal case, waiting until it will be repaired (waiting time is increases by each attempt)
				do {
					try {
						//IMPORTANT STEP1: Get new bulk of messages
						messages = Rpc.AuditMessagesManager.pollConsumerMessagesForParser(ldapcManager.getRpcCaller(), ldapProperties.getLdapConsumerName());
					} catch (InternalErrorException ex) {
						log.error("Consumer failed due to {}. Sleeping for {} ms.",ex, sleepTime);
						Thread.sleep(sleepTime);
						sleepTime+=sleepTime;
					}

					//If there are no messages, sleep for 1 sec and then try it again
					if(messages == null) Thread.sleep(1000);
				} while(messages == null);
				//If new messages exist, resolve them all
				Iterator<AuditMessage> messagesIterator = messages.iterator();
				while(messagesIterator.hasNext()) {
					message = messagesIterator.next();
					messagesIterator.remove();
					//Warning when two consecutive messages are separated by more than 15 ids
					if(lastProcessedIdNumber > 0 && lastProcessedIdNumber < message.getId()) {
						if((message.getId() - lastProcessedIdNumber) > 15) log.debug("SKIP FLAG WARNING: lastProcessedIdNumber: " + lastProcessedIdNumber + " - newMessageNumber: " + message.getId() + " = " + (lastProcessedIdNumber - message.getId()));
					}
					lastProcessedIdNumber = message.getId();
					//IMPORTANT STEP2: Resolve next message
					MessageBeans presentBeans = this.resolveMessage(message.getMsg(), message.getId());
					this.dispatchEvent(message.getMsg(), presentBeans);
				}
				//After all messages has been resolved, test interrupting of thread and if its ok, wait and go for another bulk of messages
				if (Thread.interrupted()) {
					running = false;
				} else {
					Thread.sleep(5000);
				}
			}
			//If ldapc is interrupted
		} catch (InterruptedException e) {
			Date date = new Date();
			log.error("Last message has ID='" + message.getId()+ "' and was INTERRUPTED at " + DATE_FORMAT.format(date) + " due to interrupting.");
			running = false;
			Thread.currentThread().interrupt();
			//If some other exception is thrown
		} catch (Exception e) {
			Date date = new Date();
			log.error("Last message has ID='" + message.getId() + "' and was bad PARSED or EXECUTE at " + DATE_FORMAT.format(date) + " due to exception " + e.toString());
			throw new RuntimeException(e);
		}
	}

	@Override
	public void registerProcessor(EventProcessor processor, DispatchEventCondition condition) {
		registeredProcessors.add(new Pair<DispatchEventCondition, EventProcessor>(condition, processor));
	}

	@Override
	public void dispatchEvent(String msg, MessageBeans beans) {
		for(Pair<DispatchEventCondition, EventProcessor> subscription : registeredProcessors) {
			DispatchEventCondition condition = subscription.getLeft();
			EventProcessor processor = subscription.getRight();
			
			if(condition.isApplicable(beans, msg)) {
				processor.processEvent(msg, beans);
			}
		}
	}

	protected MessageBeans resolveMessage(String msg, Integer idOfMessage) throws InternalErrorException {

		List<PerunBean> listOfBeans;
		listOfBeans = AuditParser.parseLog(msg);

		//Debug information to check parsing of message.
		MessageBeans beans = new MessageBeansImpl();
		if(!listOfBeans.isEmpty()){
			int i=0;
			for(PerunBean p: listOfBeans) {
				i++;
				if(p!=null) log.debug("There is object number " + i + ") " + p.serializeToString());
				else log.debug("There is unknow object which is null");
				beans.addBean(p);
			}
		}
		return beans;
	}

}
