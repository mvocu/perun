package cz.metacentrum.perun.ldapc.processor;

import cz.metacentrum.perun.ldapc.processor.EventDispatcher.MessageBeans;

public interface EventProcessor {
	
		public void processEvent(String msg, MessageBeans beans);
}
