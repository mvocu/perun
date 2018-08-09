package cz.metacentrum.perun.ldapc.processor.impl;

import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.processor.EventDispatcher.DispatchEventCondition;
import cz.metacentrum.perun.ldapc.processor.EventDispatcher.MessageBeans;

public abstract class AbstractDispatchEventCondition implements DispatchEventCondition {

	private int requiredBeans = 0;
	
	@Override
	public void setBeansCondition(int presentBeansMask) {
		requiredBeans = presentBeansMask;
	}

	@Override
	public void setBeansCondition(Class... beanClasses) throws InternalErrorException {
		for (Class class1 : beanClasses) {
			addFlagForBeanName(class1.getName());
		}
	}

	@Override
	public void setBeansCondition(String... names) throws InternalErrorException {
		for(String name: names) {
			addFlagForBeanName(name);
		}
	}

	@Override
	public boolean isApplicable(MessageBeans beans, String msg) {
		int presentMask = beans.getPresentBeansMask();
		
		return (requiredBeans & presentMask) == requiredBeans; 
	}

	private void addFlagForBeanName(String name) throws InternalErrorException {
		switch (name) {
		case "cz.metacentrum.perun.core.api.Attribute":
			requiredBeans |= MessageBeans.ATTRIBUTE_F;
			break;

		case "cz.metacentrum.perun.core.api.AttributeDefinition":
			requiredBeans |= MessageBeans.ATTRIBUTEDEF_F;
			break;
			
		case "cz.metacentrum.perun.core.api.Facility":
			requiredBeans |= MessageBeans.FACILITY_F;
			break;
			
		case "cz.metacentrum.perun.core.api.Group":
			requiredBeans |= MessageBeans.GROUP_F;
			break;
			
		case "cz.metacentrum.perun.core.api.Member":
			requiredBeans |= MessageBeans.MEMBER_F;
			break;
			
		case "cz.metacentrum.perun.core.api.Resource":
			requiredBeans |= MessageBeans.RESOURCE_F;
			break;
			
		case "cz.metacentrum.perun.core.api.User":
			requiredBeans |= MessageBeans.USER_F;
			break;
			
		case "cz.metacentrum.perun.core.api.UserExtSource":
			requiredBeans |= MessageBeans.USER_F;
			break;
			
		case "cz.metacentrum.perun.core.api.Vo":
			requiredBeans |= MessageBeans.VO_F;
			break;

		default:
			throw new InternalErrorException("Class " + name + " is not supported PerunBean for condition");
		}

	}
}
