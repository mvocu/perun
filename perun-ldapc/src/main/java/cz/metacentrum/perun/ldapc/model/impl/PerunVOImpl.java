package cz.metacentrum.perun.ldapc.model.impl;

import java.util.Arrays;

import javax.naming.Name;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.support.LdapNameBuilder;

import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunAttribute;
import cz.metacentrum.perun.ldapc.model.PerunUser;
import cz.metacentrum.perun.ldapc.model.PerunVO;

public class PerunVOImpl extends AbstractPerunEntry<Vo> implements PerunVO {

	private final static Logger log = LoggerFactory.getLogger(PerunVOImpl.class);

	@Autowired
	private PerunUser user;
	
	private Iterable<PerunAttribute<Vo>> defaultVOAttributes = Arrays.asList(
			new PerunAttributeDesc<>(
					PerunAttributeNames.ldapAttrOrganization, 
					PerunAttribute.REQUIRED, 
					(PerunAttributeDesc.SingleValueExtractor<Vo>)vo -> vo.getShortName()
					),
			new PerunAttributeDesc<>(
					PerunAttributeNames.ldapAttrDescription, 
					PerunAttribute.REQUIRED, 
					(PerunAttributeDesc.SingleValueExtractor<Vo>)vo -> vo.getName()
					),
			new PerunAttributeDesc<>(
					PerunAttributeNames.ldapAttrPerunVoId, 
					PerunAttribute.REQUIRED, 
					(PerunAttributeDesc.SingleValueExtractor<Vo>)vo -> vo.getId()
					)
			);
	
	public void addVo(Vo vo) throws InternalErrorException {
		addEntry(vo);
	}

	public void deleteVo(Vo vo) throws InternalErrorException {
		deleteEntry(vo);
	}

	public String getVoShortName(int voId) throws InternalErrorException {
		DirContextOperations voEntry = findById(String.valueOf(voId));
		String[] voShortNameInformation = voEntry.getStringAttributes(PerunAttributeNames.ldapAttrOrganization);
		String voShortName = null;
		if(voShortNameInformation == null || voShortNameInformation[0] == null) 
			throw new InternalErrorException("There is no shortName in ldap for vo with id=" + voId);
		if(voShortNameInformation.length != 1) 
			throw new InternalErrorException("There is not exactly one short name of vo with id=" +  voId + " in ldap. Count of shortnames is " + voShortNameInformation.length);
		voShortName = voShortNameInformation[0];
		return voShortName;
	}

	@Override
	public void addMemberToVO(int voId, Member member) {
		DirContextOperations voEntry = findById(String.valueOf(voId));
		Name memberDN = user.getEntryDN(String.valueOf(member.getUserId()));
		voEntry.addAttributeValue(PerunAttributeNames.ldapAttrUniqueMember, memberDN);
		ldapTemplate.update(voEntry);
		DirContextOperations userEntry = findByDN(memberDN);
		userEntry.addAttributeValue(PerunAttributeNames.ldapAttrMemberOfPerunVo, voId);
		ldapTemplate.update(userEntry);
	}

	@Override
	public void removeMemberFromVO(int voId, Member member) {
		DirContextOperations voEntry = findById(String.valueOf(voId));
		Name memberDN = user.getEntryDN(String.valueOf(member.getUserId()));
		voEntry.removeAttributeValue(PerunAttributeNames.ldapAttrUniqueMember, memberDN);
		ldapTemplate.update(voEntry);
		DirContextOperations userEntry = findByDN(memberDN);
		userEntry.removeAttributeValue(PerunAttributeNames.ldapAttrMemberOfPerunVo, voId);
		ldapTemplate.update(userEntry);
	}

	@Override
	protected Name buildDN(Vo bean) {
		return getEntryDN(String.valueOf(bean.getId()));
	}

	@Override
	protected void mapToContext(Vo bean, DirContextOperations context) throws InternalErrorException {
		context.setAttributeValues("objectclass", Arrays.asList(
				PerunAttributeNames.objectClassPerunVO,
				PerunAttributeNames.objectClassOrganization).toArray());
		mapToContext(bean, context, defaultVOAttributes);
	}

	@Override
	public Name getEntryDN(String ...voId) {
		return LdapNameBuilder.newInstance(getBaseDN())
				.add(PerunAttributeNames.ldapAttrPerunVoId, voId[0])
				.build();
	}

}
