package cz.metacentrum.perun.ldapc.model;

import javax.naming.directory.ModificationItem;

import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;

public interface PerunVO extends PerunEntry {

	/**
	 * Create vo in LDAP.
	 *
	 * @param vo the vo
	 * @throws InternalErrorException if NameNotFoundException is thrown
	 */
	public void createVo(Vo vo) throws InternalErrorException;

	/**
	 * Delete existing vo in LDAP.
	 *
	 * @param vo the vo
	 * @throws InternalErrorException if NameNotFoundException is thrown
	 */
	public void deleteVo(Vo vo) throws InternalErrorException;

	/**
	 * Update existing vo in LDAP.
	 * Get id from object vo.
	 *
	 * @param vo the vo
	 * @param modificationItems list of attribute which need to be modified
	 */
	public void updateVo(Vo vo, ModificationItem[] modificationItems);

	/**
	 * Update existing vo in LDAP.
	 * Use id instead of whole object.
	 *
	 * @param voId vo id
	 * @param modificationItems list of attributes which need to be modified
	 */
	public void updateVo(int voId, ModificationItem[] modificationItems);

	/**
	 * Find Vo in LDAP and return shortName of this Vo.
	 *
	 * @param voId vo id
	 *
	 * @return shortName of vo with vo id
	 * @throws InternalErrorException if shortName has not right format (null, not exists, 0 length, more than 1 shortName exist)
	 */
	public String getVoShortName(int voId) throws InternalErrorException;

}
