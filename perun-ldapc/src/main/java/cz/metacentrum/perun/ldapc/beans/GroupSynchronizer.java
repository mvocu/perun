package cz.metacentrum.perun.ldapc.beans;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.Resource;
import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PerunException;
import cz.metacentrum.perun.ldapc.model.PerunGroup;
import cz.metacentrum.perun.ldapc.service.LdapcManager;
import cz.metacentrum.perun.rpclib.Rpc;

public class GroupSynchronizer {

	private final static Logger log = LoggerFactory.getLogger(GroupSynchronizer.class);

	@Autowired
	protected LdapcManager ldapcManager;
	@Autowired
	protected PerunGroup perunGroup;
	
	public void synchronizeGroups() {
		try {
			List<Vo> vos = Rpc.VosManager.getVos(ldapcManager.getRpcCaller());

			for(Vo vo : vos) {
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("vo", new Integer(vo.getId()));
				
				try {
					List<Group> groups = ldapcManager.getRpcCaller().call("groupsManager",  "getAllGroups", params).readList(Group.class);

					for(Group group : groups) {

						perunGroup.synchronizeEntry(group);

						params.clear();
						params.put("group", new Integer(group.getId()));

						List<Member> members = ldapcManager.getRpcCaller().call("groupsManager",  "getGroupMembers", params).readList(Member.class);
						perunGroup.synchronizeMembers(group, members);
						
						List<Resource> resources = Rpc.ResourcesManager.getAssignedResources(ldapcManager.getRpcCaller(), group);
						perunGroup.synchronizeResources(group, resources);
					}
				} catch (PerunException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
			}
		} catch (InternalErrorException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
}
