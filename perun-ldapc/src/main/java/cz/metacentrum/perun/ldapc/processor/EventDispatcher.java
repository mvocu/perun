package cz.metacentrum.perun.ldapc.processor;

import cz.metacentrum.perun.core.api.PerunBean;

public interface EventDispatcher extends Runnable {

	public interface MessageBeans {

		public void addBean(PerunBean p);
		
	}
	
	public interface DispatchEventCondition {

		public boolean isApplicable(MessageBeans beans, String msg);
	}
	
	public void registerProcessor(EventProcessor processor, DispatchEventCondition condition);

	public void dispatchEvent(String msg, MessageBeans beans);
}
