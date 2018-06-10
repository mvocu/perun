package cz.metacentrum.perun.ldapc.model.impl;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.ModificationItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.NameNotFoundException;

import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunVO;
import cz.metacentrum.perun.ldapc.model.impl.AbstractPerunEntry.PerunAttributeNames;

public class PerunVOImpl extends AbstractPerunEntry implements PerunVO {

	private final static Logger log = LoggerFactory.getLogger(PerunVOImpl.class);
	
	public void createVo(Vo vo) throws InternalErrorException {
		// Create a set of attributes for vo
		Attributes voAttributes = new BasicAttributes();

		// Create the objectclass to add
		Attribute voObjClasses = new BasicAttribute(PerunAttributeNames.ldapAttrObjectClass);
		voObjClasses.add(PerunAttributeNames.objectClassTop);
		voObjClasses.add(PerunAttributeNames.objectClassOrganization);
		voObjClasses.add(PerunAttributeNames.objectClassPerunVO);

		// Add attributes
		voAttributes.put(voObjClasses);
		voAttributes.put(PerunAttributeNames.ldapAttrOrganization, vo.getShortName());
		voAttributes.put(PerunAttributeNames.ldapAttrDescription, vo.getName());
		voAttributes.put(PerunAttributeNames.ldapAttrPerunVoId, String.valueOf(vo.getId()));

		// Create the entires
		try {
			ldapTemplate.bind(getVoDNByVoId(String.valueOf(vo.getId())), null, voAttributes);
			log.debug("New entry created in LDAP: Vo {}.", vo);
		} catch (NameNotFoundException e) {
			throw new InternalErrorException(e);
		}
	}

	public void deleteVo(Vo vo) throws InternalErrorException {
		try {
			ldapTemplate.unbind(getVoDNByVoId(String.valueOf(vo.getId())));
			log.debug("Entry deleted from LDAP: Vo {}.", vo);
		} catch (NameNotFoundException e) {
			throw new InternalErrorException(e);
		}
	}

	public void updateVo(Vo vo, ModificationItem[] modificationItems) {
		ldapTemplate.modifyAttributes(getVoDNByVoId(String.valueOf(vo.getId())), modificationItems);
		log.debug("Entry modified in LDAP: Vo {}.", vo);
	}

	public void updateVo(int voId, ModificationItem[] modificationItems) {
		ldapTemplate.modifyAttributes(getVoDNByVoId(String.valueOf(voId)), modificationItems);
		log.debug("Entry modified in LDAP: Vo {}.", voId);
	}

	public String getVoShortName(int voId) throws InternalErrorException {
		Object o = ldapTemplate.lookup(getVoDNByVoId(String.valueOf(voId)), new VoShortNameContextMapper());
		String[] voShortNameInformation = (String []) o;
		String voShortName = null;
		if(voShortNameInformation == null || voShortNameInformation[0] == null) throw new InternalErrorException("There is no shortName in ldap for vo with id=" + voId);
		if(voShortNameInformation.length != 1) throw new InternalErrorException("There is not exactly one short name of vo with id=" +  voId + " in ldap. Count of shortnames is " + voShortNameInformation.length);
		voShortName = voShortNameInformation[0];
		return voShortName;
	}

	/**
	 * Get Vo DN using VoId.
	 *
	 * @param voId vo id
	 * @return DN in String
	 */
	protected String getVoDNByVoId(String voId) {
		return new StringBuffer()
			.append(PerunAttributeNames.ldapAttrPerunVoId + "=")
			.append(voId)
			.toString();
	}

	/**
	 * Get Vo DN using Vo shortName (o).
	 *
	 * @param voShortName the value of attribute 'o' in ldap
	 * @return DN in String
	 */
	protected String getVoDNByShortName(String voShortName) {
		return new StringBuffer()
			.append(PerunAttributeNames.ldapAttrOrganization + "=")
			.append(voShortName)
			.toString();
	}

}
