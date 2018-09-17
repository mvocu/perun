package cz.metacentrum.perun.ldapc.beans;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PerunException;
import cz.metacentrum.perun.ldapc.model.PerunVO;
import cz.metacentrum.perun.ldapc.service.LdapcManager;
import cz.metacentrum.perun.rpclib.Rpc;

public class VOSynchronizer {

	private final static Logger log = LoggerFactory.getLogger(VOSynchronizer.class);

	@Autowired
	protected LdapcManager ldapcManager;
	@Autowired
	protected PerunVO perunVO;
	
	public void synchronizeVOs() {
		try {
			List<Vo> vos = Rpc.VosManager.getVos(ldapcManager.getRpcCaller());
			for (Vo vo : vos) {
				Map<String, Object> params = new HashMap<String, Object>();
				params.put("vo", new Integer(vo.getId()));
				
				perunVO.synchronizeEntry(vo);
				try {
					List<Member> members = ldapcManager.getRpcCaller().call("membersManager", "getMembers", params).readList(Member.class);
					perunVO.synchronizeMembers(vo, members);
				} catch (PerunException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch (InternalErrorException e) {
		}
	}
}
