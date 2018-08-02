package cz.metacentrum.perun.ldapc.processor;

public interface DispatchEventCondition {

		public boolean isApplicable(Integer beansMask, String msg);
}
