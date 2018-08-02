package cz.metacentrum.perun.ldapc.processor;

import cz.metacentrum.perun.core.api.PerunBean;

public interface EventDispatcher {
	
	public void registerProcessor(EventProcessor processor, DispatchEventCondition condition);

	public void dispatchEvent(String msg, PerunBean ...beans);
}
