package cz.metacentrum.perun.ldapc.model;

import javax.naming.directory.Attributes;
import javax.naming.directory.ModificationItem;

import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;

public interface PerunUser extends PerunEntry {

	/**
	 * Create user in ldap.
	 *
	 * @param user user from perun
	 * @throws InternalErrorException if NameNotFoundException occurs
	 */
	public void createUser(User user) throws InternalErrorException;

	/**
	 * Update existing user in ldap.
	 *
	 * @param user user from perun
	 * @param modificationItems list of attributes which need to be modified
	 */
	public void updateUser(User user, ModificationItem[] modificationItems);

	/**
	 * Update existing user in ldap.
	 *
	 * @param userId use id instead of whole object user
	 * @param modificationItems list of attributes which need to be modified
	 */
	public void updateUserWithUserId(String userId, ModificationItem[] modificationItems);

	/**
	 * Delete existing user from ldap.
	 * IMPORTANT Don't need delete members of deleting user from groups, it will depend on messages removeFrom Group
	 *
	 * @param user
	 * @throws InternalErrorException
	 */
	public void deleteUser(User user) throws InternalErrorException;

	/**
	 * Update all values of user attribute
	 *
	 * @param userId user id
	 * @param records values of attribute
	 */
	public void updateUsersAttributeInLDAP(String userId, String ldapAttrName, String[] records);

	/**
	 * Return true if user already exists in ldap.
	 *
	 * @param user user in perun
	 * @return true if user already exists in ldap, false if not
	 */
	public boolean userExist(User user);

	/**
	 * Return true if user attribute with ldapAttributeName in ldap exists.
	 *
	 * @param user user in perun
	 * @param ldapAttributeName name of user ldap attribute
	 * @return true if attribute in ldap exists, false if not
	 * @throws InternalErrorException if ldapAttributeName is null
	 */
	public boolean userAttributeExist(User user, String ldapAttributeName) throws InternalErrorException;

	/**
	 * Return true if user attribute 'password' in ldap already exists.
	 *
	 * @param user user in perun
	 * @return true if password in ldap exists for user, false if note
	 */
	public boolean userPasswordExists(User user);

	/**
	 * Get all ldapAttributes of user
	 *
	 * @param user user in perun
	 * @return all attribute of user in ldap
	 * @throws InternalErrorException
	 */
	public Attributes getAllUsersAttributes(User user);
}
