package cz.metacentrum.perun.ldapc.model;

import javax.naming.directory.ModificationItem;

import cz.metacentrum.perun.core.api.Resource;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;

public interface PerunResource extends PerunEntry {

	/**
	 * Remove resource from LDAP
	 *
	 * @param resource resource from Perun
	 * @throws InternalErrorException if NameNotFoundException is thrown
	 */
	public void deleteResource(Resource resource) throws InternalErrorException;

	/**
	 * Update resource in LDAP
	 *
	 * @param resource resource from Perun
	 * @param modificationItems attributes of resources which need to be modified
	 * @throws InternalErrorException if NameNotFoundException is thrown
	 */
	public void updateResource(Resource resource, ModificationItem[] modificationItems) throws InternalErrorException;

	/**
	 * Add resource to LDAP.
	 *
	 * @param resource resource from Perun
	 * @param entityID entityID if exists, or null if not
	 * @throws InternalErrorException if NameNotFoundException is thrown
	 */
	public void createResource(Resource resource, String entityID) throws InternalErrorException;

	/**
	 * Return true if resource attribute with ldapAttributeName in ldap exists.
	 *
	 * @param resource resource in perun
	 * @param ldapAttributeName name of user ldap attribute
	 * @return true if attribute in ldap exists, false if not
	 * @throws InternalErrorException if ldapAttributeName is null
	 */
	public boolean resourceAttributeExist(Resource resource, String ldapAttributeName) throws InternalErrorException;

}
