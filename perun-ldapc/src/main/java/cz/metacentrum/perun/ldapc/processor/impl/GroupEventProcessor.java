package cz.metacentrum.perun.ldapc.processor.impl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.MemberNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.PrivilegeException;
import cz.metacentrum.perun.ldapc.model.PerunAttribute;
import cz.metacentrum.perun.ldapc.processor.EventDispatcher.MessageBeans;
import cz.metacentrum.perun.rpclib.Rpc;

public class GroupEventProcessor extends AbstractEventProcessor {

	private final static Logger log = LoggerFactory.getLogger(GroupEventProcessor.class);

	@Override
	public void processEvent(String msg, MessageBeans beans) {
	}

	public void processMemberAdded(String msg, MessageBeans beans) {
		if(beans.getGroup() == null || beans.getMember() == null) {
			return;
		}
		try {
			perunGroup.addMemberToGroup(beans.getMember(), beans.getGroup());
		} catch (InternalErrorException e) {
		}
	}

	public void processSubgroupAdded(String msg, MessageBeans beans) {
		if(beans.getGroup() == null || beans.getParentGroup() == null) {
			return;
		}
		try {
			perunGroup.addGroupAsSubGroup(beans.getGroup(), beans.getParentGroup());
		} catch (InternalErrorException e) {
		}
	}

	public void processResourceAssigned(String msg, MessageBeans beans) {
		if(beans.getGroup() == null || beans.getResource() == null) {
			return;
		}
		try {
			perunResource.assignGroup(beans.getResource(), beans.getGroup());
		} catch (InternalErrorException e) {
		}
	}

	public void processResourceRemoved(String msg, MessageBeans beans) {
		if(beans.getGroup() == null || beans.getResource() == null) {
			return;
		}
		try {
			perunResource.removeGroup(beans.getResource(), beans.getGroup());
		} catch (InternalErrorException e) {
		}
	}

	public void processGroupMoved(String msg, MessageBeans beans) {
		if(beans.getGroup() == null) {
			return;
		}
		try {
			// TODO move to PerunGroupImpl?
			perunGroup.modifyEntry(beans.getGroup(), 
						PerunAttribute.PerunAttributeNames.ldapAttrCommonName,
						PerunAttribute.PerunAttributeNames.ldapAttrPerunUniqueGroupName,
						PerunAttribute.PerunAttributeNames.ldapAttrPerunParentGroup,
						PerunAttribute.PerunAttributeNames.ldapAttrPerunParentGroupId);
		} catch (InternalErrorException e) {
		}
		
	}

	public void processMemberValidated(String msg, MessageBeans beans) {
		if(beans.getMember() == null) {
			return;
		}
		List<Group> memberGroups = new ArrayList<Group>();
		try {
			memberGroups = Rpc.GroupsManager.getAllMemberGroups(ldapcManager.getRpcCaller(), beans.getMember());
			for(Group g: memberGroups) {
				perunGroup.addMemberToGroup(beans.getMember(), g);
			}
		} catch (MemberNotExistsException e) {
			//IMPORTANT this is not problem, if member not exist, we expected that will be deleted in some message after that, in DB is deleted
		} catch (PrivilegeException e) {
			log.warn("There are no privilegies for getting member's groups", e);
		} catch (InternalErrorException e) {
			log.debug("", e);
		}
	}

	public void processMemberInvalidated(String msg, MessageBeans beans) {
		if(beans.getMember() == null) {
			return;
		}
		List<Group> memberGroups = new ArrayList<Group>();
		try {
			memberGroups = Rpc.GroupsManager.getAllMemberGroups(ldapcManager.getRpcCaller(), beans.getMember());
			for(Group g: memberGroups) {
				perunGroup.removeMemberFromGroup(beans.getMember(), g);
			}
		} catch (MemberNotExistsException e) {
			//IMPORTANT this is not problem, if member not exist, we expected that will be deleted in some message after that, in DB is deleted
		} catch (PrivilegeException e) {
			log.warn("There are no privilegies for getting member's groups", e);
		} catch (InternalErrorException e) {
			log.debug("", e);
		}
	}

}
