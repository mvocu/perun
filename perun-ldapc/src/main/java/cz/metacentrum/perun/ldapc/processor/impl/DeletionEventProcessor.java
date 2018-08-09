package cz.metacentrum.perun.ldapc.processor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.processor.EventDispatcher.MessageBeans;

public class DeletionEventProcessor extends AbstractEventProcessor {

	private final static Logger log = LoggerFactory.getLogger(DeletionEventProcessor.class);
	
	@Override
	public void processEvent(String msg, MessageBeans beans) {
		for(int beanFlag: beans.getPresentBeansFlags()) {
			try {
				switch(beanFlag) {
				case MessageBeans.GROUP_F:
					perunGroup.removeGroup(beans.getGroup());
					break;
					
				case MessageBeans.RESOURCE_F:
					perunResource.deleteResource(beans.getResource());
					break;
					
				case MessageBeans.USER_F:
					perunUser.deleteUser(beans.getUser());
					break;
					
				case MessageBeans.VO_F:
					perunVO.deleteVo(beans.getVo());
					break;
					
				default:
					break;	
				}
			} catch(InternalErrorException e) {
				
			}
		}
	}

}
