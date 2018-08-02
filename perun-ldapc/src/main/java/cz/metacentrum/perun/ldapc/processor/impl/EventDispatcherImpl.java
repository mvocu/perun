package cz.metacentrum.perun.ldapc.processor.impl;

import java.util.List;

import cz.metacentrum.perun.core.api.Pair;
import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.ldapc.processor.DispatchEventCondition;
import cz.metacentrum.perun.ldapc.processor.EventDispatcher;
import cz.metacentrum.perun.ldapc.processor.EventProcessor;

public class EventDispatcherImpl implements EventDispatcher, Runnable {

	private List<Pair<DispatchEventCondition, EventProcessor>> registeredProcessors;

	private Integer presentBeans;
	
	@Override
	public void run() {

	}

	@Override
	public void registerProcessor(EventProcessor processor, DispatchEventCondition condition) {
		registeredProcessors.add(new Pair<DispatchEventCondition, EventProcessor>(condition, processor));
	}

	@Override
	public void dispatchEvent(String msg, PerunBean... beans) {
		for(Pair<DispatchEventCondition, EventProcessor> subscription : registeredProcessors) {
			DispatchEventCondition condition = subscription.getLeft();
			EventProcessor processor = subscription.getRight();
			
			if(condition.isApplicable(presentBeans, msg)) {
				processor.processEvent(msg, beans);
			}
		}
	}

}
