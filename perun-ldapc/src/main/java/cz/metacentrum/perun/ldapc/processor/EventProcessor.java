package cz.metacentrum.perun.ldapc.processor;

import cz.metacentrum.perun.core.api.PerunBean;

public interface EventProcessor {
	
		public void processEvent(String msg, PerunBean ...beans);
}
