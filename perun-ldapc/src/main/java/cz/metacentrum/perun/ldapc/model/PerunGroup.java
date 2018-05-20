package cz.metacentrum.perun.ldapc.model;

import java.util.List;

import javax.naming.directory.ModificationItem;

import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;

public interface PerunGroup extends PerunEntry {

	/**
	 * Add group to LDAP.
	 *
	 * @param group group from Perun
	 *
	 * @throws InternalErrorException if NameNotFoundException is thrown
	 */
	public void addGroup(Group group) throws InternalErrorException;

	/**
	 * Remove group from LDAP
	 *
	 * @param group group from Perun
	 * @throws InternalErrorException if NameNotFoundException is thrown
	 */
	public void removeGroup(Group group) throws InternalErrorException;

	/**
	 * Update group in LDAP
	 *
	 * @param group new state of group in perun
	 * @param modificationItems attributes of groups which need to be modified
	 */
	public void updateGroup(Group group, ModificationItem[] modificationItems);

	/**
	 * The same behavior like method 'addGroup'.
	 * Only call method 'addGroup' with group
	 *
	 * @param group group from Perun (then call addGroup(group) )
	 * @param parentGroup (is not used now, can be null) IMPORTANT
	 *
	 * @throws InternalErrorException if NameNotFoundException is thrown
	 */
	public void addGroupAsSubGroup(Group group, Group parentGroup) throws InternalErrorException;

	/**
	 * Return true if group attribute with ldapAttributeName in ldap exists - it is set for group.
	 *
	 * @param group group in perun
	 * @param ldapAttributeName name of group ldap attribute
	 * @return true if attribute in ldap exists, false if not
	 * @throws InternalErrorException if ldapAttributeName is null
	 */
	public boolean groupAttributeExist(Group group, String ldapAttributeName) throws InternalErrorException;

	//-----------------------------MEMBER METHODS---------------------------------
	/**
	 * Add member to group in LDAP.
	 * It means add attribute to member and add attribute to group.
	 * If this group is 'members' group, add member attribute to the vo (of group) too.
	 *
	 * @param member the member
	 * @param group the group
	 *
	 * @throws InternalErrorException if NameNotFoundException is thrown
	 */
	public void addMemberToGroup(Member member, Group group) throws InternalErrorException;

	/**
	 * Remove member from group in LDAP.
	 * It means remove attribute from member and remove attribute from group.
	 * If this group is 'member' group, remove member attribute for vo (of group) too.
	 *
	 * @param member
	 * @param group
	 * @throws InternalErrorException if NameNotFoundException is thrown
	 */
	public void removeMemberFromGroup(Member member, Group group) throws InternalErrorException;

	/**
	 * Return true if member has already attribute 'memberOf' for this group in LDAP
	 *
	 * @param member the member
	 * @param group the group
	 * @return true if attribute 'memberOf' exists for this group, false if not
	 */
	public boolean isAlreadyMember(Member member, Group group);

	/**
	 * Get all 'uniqueMember' values of group in LDAP.
	 *
	 * @param groupId group Id
	 * @param voId vo Id
	 * @return list of uniqueMember values
	 */
	public List<String> getAllUniqueMembersInGroup(int groupId, int voId);

}
