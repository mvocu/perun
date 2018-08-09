package cz.metacentrum.perun.ldapc.processor.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.metacentrum.perun.core.api.AttributesManager;
import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.exceptions.AttributeNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.FacilityNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PrivilegeException;
import cz.metacentrum.perun.core.api.exceptions.WrongAttributeAssignmentException;
import cz.metacentrum.perun.ldapc.model.PerunAttribute;
import cz.metacentrum.perun.ldapc.processor.EventDispatcher.MessageBeans;
import cz.metacentrum.perun.rpclib.Rpc;

public class CreationEventProcessor extends AbstractEventProcessor {

	private final static Logger log = LoggerFactory.getLogger(DeletionEventProcessor.class);
	
	@Override
	public void processEvent(String msg, MessageBeans beans) {
		for(int beanFlag: beans.getPresentBeansFlags()) {
			try {
				switch(beanFlag) {
				case MessageBeans.GROUP_F:
					perunGroup.addGroup(beans.getGroup());
					break;
					
				case MessageBeans.RESOURCE_F:
					perunResource.addResource(beans.getResource(), getFacilityEntityIdValue(beans.getResource().getFacilityId()));
					break;
					
				case MessageBeans.USER_F:
					perunUser.addUser(beans.getUser());
					break;
					
				case MessageBeans.VO_F:
					perunVO.addVo(beans.getVo());
					break;
					
				default:
					break;	
				}
			} catch(InternalErrorException e) {
				
			}
		}

	}

	/**
	 * Get entityID value from perun by facilityId.
	 *
	 * @param facilityId the facilityId
	 * @return value of entityID or null, if value is null or user not exists yet
	 * @throws InternalErrorException if some exception is thrown from RPC
	 */
	private String getFacilityEntityIdValue(int facilityId) throws InternalErrorException {
		Facility facility = null;
		try {
			facility = Rpc.FacilitiesManager.getFacilityById(ldapcManager.getRpcCaller(), facilityId);
		} catch (PrivilegeException ex) {
			throw new InternalErrorException("There are no privilegies for getting facility by id.", ex);
		} catch (FacilityNotExistsException ex) {
			//If facility not exist in perun now, probably will be deleted in next step so its ok. The value is null anyway.
			return null;
		}

		cz.metacentrum.perun.core.api.Attribute entityID = null;
		try {
			entityID = Rpc.AttributesManager.getAttribute(ldapcManager.getRpcCaller(), facility, AttributesManager.NS_FACILITY_ATTR_DEF + ":" + PerunAttribute.PerunAttributeNames.perunAttrEntityID);
		} catch(PrivilegeException ex) {
			throw new InternalErrorException("There are no privilegies for getting facility attribute.", ex);
		} catch(AttributeNotExistsException ex) {
			throw new InternalErrorException("There is no such attribute.", ex);
		} catch(FacilityNotExistsException ex) {
			//If facility not exist in perun now, probably will be deleted in next step so its ok. The value is null anyway.
			return null;
		} catch(WrongAttributeAssignmentException ex) {
			throw new InternalErrorException("There is problem with wrong attribute assignment exception.", ex);
		}
		if(entityID.getValue() == null) return null;
		else return (String) entityID.getValue();
	}


}
