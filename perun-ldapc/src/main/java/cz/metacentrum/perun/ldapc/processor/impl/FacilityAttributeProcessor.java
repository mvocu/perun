package cz.metacentrum.perun.ldapc.processor.impl;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.metacentrum.perun.core.api.Resource;
import cz.metacentrum.perun.core.api.exceptions.FacilityNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PrivilegeException;
import cz.metacentrum.perun.ldapc.processor.EventDispatcher.MessageBeans;
import cz.metacentrum.perun.rpclib.Rpc;

public class FacilityAttributeProcessor extends AbstractAttributeProcessor {

	private final static Logger log = LoggerFactory.getLogger(FacilityAttributeProcessor.class);

	private static Pattern facilitySetPattern = Pattern.compile(" set for Facility:\\[(.*)\\]");
	private static Pattern facilityRemovePattern = Pattern.compile(" removed for Facility:\\[(.*)\\]");
	private static Pattern facilityAllAttrsRemovedPattern = Pattern.compile("All attributes removed for Facility:\\[(.*)\\]");

	public FacilityAttributeProcessor() {
		super(MessageBeans.FACILITY_F, facilitySetPattern, facilityRemovePattern, facilityAllAttrsRemovedPattern);
	}

	public void processAttributeSet(String msg, MessageBeans beans) {
		// ensure we have the correct beans available
		if(beans.getAttribute() == null || beans.getFacility() == null) {
			return;
		}
		try {
			log.debug("Getting resources assigned to facility {}", beans.getFacility().getId());
			List<Resource> resources = Rpc.FacilitiesManager.getAssignedResources(ldapcManager.getRpcCaller(), beans.getFacility());
			for(Resource resource: resources) {
				log.debug("Setting attribute {} for resource {}", beans.getAttribute(), resource);
				perunResource.modifyEntry(resource, beans.getAttribute());
			}
		} catch (FacilityNotExistsException | InternalErrorException | PrivilegeException e) {
				log.error("Error setting attribute:", e);
		}
	}

	public void processAttributeRemoved(String msg, MessageBeans beans) {
		// ensure we have the correct beans available
		if(beans.getAttributeDef() == null || beans.getFacility() == null) {
			return;
		}
		try {
			log.debug("Getting resources assigned to facility {}", beans.getFacility().getId());
			List<Resource> resources = Rpc.FacilitiesManager.getAssignedResources(ldapcManager.getRpcCaller(), beans.getFacility());
			for(Resource resource: resources) {
				log.debug("Removing attribute {} from resource {}", beans.getAttributeDef(), resource);
				perunResource.modifyEntry(resource, beans.getAttributeDef());
			}
		} catch (FacilityNotExistsException | InternalErrorException | PrivilegeException e) {
			log.error("Error removing attribute:", e);
		}
	}

	public void processAllAttributesRemoved(String msg, MessageBeans beans) {
		// ensure we have the correct beans available
		if(beans.getUser() == null) {
			return;
		}
		try {
			log.debug("Getting resources assigned to facility {}", beans.getFacility().getId());
			List<Resource> resources = Rpc.FacilitiesManager.getAssignedResources(ldapcManager.getRpcCaller(), beans.getFacility());
			for(Resource resource: resources) {
				log.debug("Removing all attributes from resource {}", resource);
				perunResource.removeAllAttributes(resource);
			}
		} catch (FacilityNotExistsException | InternalErrorException | PrivilegeException e) {
			log.error("Error removing attributes:", e);
		}
	}	

}
