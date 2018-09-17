package cz.metacentrum.perun.ldapc.beans;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Resource;
import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PerunException;
import cz.metacentrum.perun.ldapc.model.PerunResource;
import cz.metacentrum.perun.ldapc.service.LdapcManager;
import cz.metacentrum.perun.rpclib.Rpc;

public class ResourceSynchronizer {

	private final static Logger log = LoggerFactory.getLogger(ResourceSynchronizer.class);

	@Autowired
	protected LdapcManager ldapcManager;
	@Autowired
	protected PerunResource perunResource;
	
	public void synchronizeResources() {
		try {
			List<Vo> vos = Rpc.VosManager.getVos(ldapcManager.getRpcCaller());
			for (Vo vo : vos) {
				Map<String, Object> params = new HashMap <String, Object>();
				params.put("vo", new Integer(vo.getId()));
				
				try {
					List<Resource> resources = ldapcManager.getRpcCaller().call("resourceManager", "getResources", params).readList(Resource.class);

					for(Resource resource: resources) {
						
						Facility facility = Rpc.ResourcesManager.getFacility(ldapcManager.getRpcCaller(), resource);
						
						params.clear();
						params.put("facility",  new Integer(facility.getId()));
						
						List<Attribute> attrs = ldapcManager.getRpcCaller().call("attributesManager", "getAttributes", params).readList(Attribute.class);
						
						perunResource.synchronizeEntry(resource, attrs);
					}
				} catch (PerunException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch (InternalErrorException e) {
		}

	}
	
}
