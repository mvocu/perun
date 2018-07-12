package cz.metacentrum.perun.ldapc.model.impl;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.Name;
import javax.naming.directory.ModificationItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.support.LdapNameBuilder;

import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.VosManager;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunAttribute;
import cz.metacentrum.perun.ldapc.model.PerunGroup;
import cz.metacentrum.perun.ldapc.model.PerunUser;
import cz.metacentrum.perun.ldapc.model.PerunVO;

public class PerunGroupImpl extends AbstractPerunEntry<Group> implements PerunGroup {

	private final static Logger log = LoggerFactory.getLogger(PerunGroupImpl.class);

	@Autowired
	private PerunVO vo;
	@Autowired
	private PerunUser user;

	private Iterable<PerunAttribute<Group>> defaultGroupAttributes = Arrays.asList(
			new PerunAttributeDesc<>(
					PerunAttributeNames.ldapAttrCommonName, 
					PerunAttribute.REQUIRED, 
					(PerunAttributeDesc.SingleValueExtractor<Group>)group -> group.getName()
					),
			new PerunAttributeDesc<>(
					PerunAttributeNames.ldapAttrPerunGroupId, 
					PerunAttribute.REQUIRED,
					(PerunAttributeDesc.SingleValueExtractor<Group>)group -> group.getId()
					),
			new PerunAttributeDesc<>(
					PerunAttributeNames.ldapAttrPerunUniqueGroupName,
					PerunAttributeDesc.REQUIRED,
					(PerunAttributeDesc.SingleValueExtractor<Group>)group -> vo.getVoShortName(group.getVoId()) + ":" + group.getName()
					),					
			new PerunAttributeDesc<>(
					PerunAttributeNames.ldapAttrPerunVoId,
					PerunAttributeDesc.REQUIRED,
					(PerunAttributeDesc.SingleValueExtractor<Group>)group -> group.getVoId()
					),
			new PerunAttributeDesc<>(
					PerunAttributeNames.ldapAttrDescription,
					PerunAttributeDesc.OPTIONAL,
					(PerunAttributeDesc.SingleValueExtractor<Group>)group -> group.getDescription()
					),
			new PerunAttributeDesc<>(
					PerunAttributeNames.ldapAttrPerunParentGroup,
					PerunAttributeDesc.OPTIONAL,
					(PerunAttributeDesc.SingleValueExtractor<Group>)group -> this.getEntryDN(String.valueOf(group.getParentGroupId()))
					// PerunAttributeNames.ldapAttrPerunGroupId + "=" + group.getParentGroupId().toString() + "," + PerunAttributeNames.ldapAttrPerunVoId + "=" + group.getVoId() + "," + ldapProperties.getLdapBase()
					),
			new PerunAttributeDesc<>(
					PerunAttributeNames.ldapAttrPerunParentGroupId,
					PerunAttributeDesc.OPTIONAL,
					(PerunAttributeDesc.SingleValueExtractor<Group>)group -> group.getParentGroupId().toString()
					)
			);
			

	public void addGroup(Group group) throws InternalErrorException {
		addEntry(group);
	}

	public void addGroupAsSubGroup(Group group, Group parentGroup) throws InternalErrorException {
		//This method has the same implementation like 'addGroup'
		addGroup(group);
	}

	public void removeGroup(Group group) throws InternalErrorException {
		Name groupDN = buildDN(group);
		DirContextOperations groupEntry = findByDN(groupDN);
		String[] uniqueMembers = groupEntry.getStringAttributes(PerunAttributeNames.ldapAttrUniqueMember);
		if(uniqueMembers != null)
			for(String memberDN: uniqueMembers) {
				DirContextOperations memberEntry = user.findByDN(LdapNameBuilder.newInstance(memberDN).build());
				memberEntry.removeAttributeValue(PerunAttributeNames.ldapAttrMemberOf, groupDN);
				ldapTemplate.update(memberEntry);
			}
		
		deleteEntry(group);
	}

	public void addMemberToGroup(Member member, Group group) throws InternalErrorException {
		//Add member to group
		Name groupDN = buildDN(group);
		DirContextOperations groupEntry = findByDN(groupDN);
		Name memberDN = user.getEntryDN(String.valueOf(member.getUserId()));
		groupEntry.addAttributeValue(PerunAttributeNames.ldapAttrUniqueMember, memberDN);
		ldapTemplate.update(groupEntry);
		
		//Add member to vo if this group is membersGroup
		if(group.getName().equals(VosManager.MEMBERS_GROUP) && group.getParentGroupId() == null) {
			//Add info to vo
			vo.addMemberToVO(group.getVoId(), member);
		}
		//Add group info to member
		// user->add('memberOf' => groupDN)
		DirContextOperations userEntry = findByDN(memberDN);
		userEntry.addAttributeValue(PerunAttributeNames.ldapAttrMemberOf, groupDN);
		ldapTemplate.update(userEntry);
	}

	public void removeMemberFromGroup(Member member, Group group) throws InternalErrorException {
		//Remove member from group
		Name groupDN = buildDN(group);
		DirContextOperations groupEntry = findByDN(groupDN);
		Name memberDN = user.getEntryDN(String.valueOf(member.getUserId()));
		groupEntry.removeAttributeValue(PerunAttributeNames.ldapAttrUniqueMember, memberDN);
		ldapTemplate.update(groupEntry);

		//Remove member from vo if this group is membersGroup
		if(group.getName().equals(VosManager.MEMBERS_GROUP) && group.getParentGroupId() == null) {
			//Remove info from vo
			vo.removeMemberFromVO(group.getVoId(), member);
		}
		//Remove group info from member
		DirContextOperations userEntry = findByDN(memberDN);
		userEntry.removeAttributeValue(PerunAttributeNames.ldapAttrMemberOf, groupDN);
		ldapTemplate.update(userEntry);
	}

	public boolean isAlreadyMember(Member member, Group group) {
		DirContextOperations groupEntry = findByDN(buildDN(group));
		Name userDN = user.getEntryDN(String.valueOf(member.getUserId()));
		String[] memberOfInformation = groupEntry.getStringAttributes(PerunAttributeNames.ldapAttrUniqueMember);
		if(memberOfInformation != null) {
			for(String s: memberOfInformation) {
				Name memberDN = LdapNameBuilder.newInstance(s).build();
				if(memberDN.compareTo(userDN) == 0)
					// TODO should probably cross-check the user.memberOf attribute
					return true;
			}
		}
		return false;
	}


	@Deprecated
	public List<String> getAllUniqueMembersInGroup(int groupId, int voId) {
		Pattern userIdPattern = Pattern.compile("[0-9]+");
		List<String> uniqueMembers = new ArrayList<String>();
		DirContextOperations groupEntry = findById(String.valueOf(groupId), String.valueOf(voId));
		String[] uniqueGroupInformation = groupEntry.getStringAttributes(PerunAttributeNames.ldapAttrUniqueMember);
		if(uniqueGroupInformation != null) {
			for(String s: uniqueGroupInformation) {
				Matcher userIdMatcher = userIdPattern.matcher(s);
				if(userIdMatcher.find()) 
					uniqueMembers.add(s.substring(userIdMatcher.start(), userIdMatcher.end()));
			}
		}
		return uniqueMembers;
	}

	@Override
	protected void mapToContext(Group group, DirContextOperations context) throws InternalErrorException {
		context.setAttributeValue("objectclass", PerunAttributeNames.objectClassPerunGroup);
		mapToContext(group, context, defaultGroupAttributes);
	}

	@Override
	protected Name buildDN(Group group) {
		return getEntryDN(String.valueOf(group.getVoId()), String.valueOf(group.getId()));
	}
	
	/**
	 * Get Group DN using VoId and GroupId.
	 *
	 * @param voId vo id
	 * @param groupId group id
	 * @return DN in String
	 */
	@Override
	public Name getEntryDN(String ...id) {
		return LdapNameBuilder.newInstance(getBaseDN())
				.add(PerunAttributeNames.ldapAttrPerunVoId, id[0])
				.add(PerunAttributeNames.ldapAttrPerunGroupId, id[1])
				.build();
	}

}
