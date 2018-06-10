package cz.metacentrum.perun.ldapc.model.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.retry.policy.MapRetryContextCache;

import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.VosManager;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunGroup;
import cz.metacentrum.perun.ldapc.model.PerunUser;
import cz.metacentrum.perun.ldapc.model.PerunVO;

public class PerunGroupImpl extends AbstractPerunEntry implements PerunGroup {

	private final static Logger log = LoggerFactory.getLogger(PerunGroupImpl.class);

	@Autowired
	private PerunVO vo;
	@Autowired
	private PerunUser user;

	public void addGroup(Group group) throws InternalErrorException {
		DirContextAdapter context = new DirContextAdapter(getGroupDN(String.valueOf(group.getVoId()), String.valueOf(group.getId())));
		mapToContext(group, context);
		ldapTemplate.bind(context);
	}

	public void addGroupAsSubGroup(Group group, Group parentGroup) throws InternalErrorException {
		//This method has the same implemenation like 'addGroup'
		addGroup(group);
	}

	public void removeGroup(Group group) throws InternalErrorException {

		for(String s: getAllUniqueMembersInGroup(group.getId(), group.getVoId())) {
			Attribute memberOf = new BasicAttribute(PerunAttributeNames.ldapAttrMemberOf, PerunAttributeNames.ldapAttrPerunGroupId + "=" + group.getId() + "," + PerunAttributeNames.ldapAttrPerunVoId + "=" + group.getVoId() + "," + ldapProperties.getLdapBase());
			ModificationItem memberOfItem = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, memberOf);
			user.updateUserWithUserId(s, new ModificationItem[] {memberOfItem});
		}

		try {
			ldapTemplate.unbind(getGroupDN(String.valueOf(group.getVoId()), String.valueOf(group.getId())));
			log.debug("Entry deleted from LDAP: Group {} from Vo with ID=" + group.getVoId() + ".", group);
		} catch (NameNotFoundException e) {
			throw new InternalErrorException(e);
		}
	}

	public void updateGroup(Group group, ModificationItem[] modificationItems) {
		ldapTemplate.modifyAttributes(getGroupDN(String.valueOf(group.getVoId()), String.valueOf(group.getId())), modificationItems);
		log.debug("Entry modified in LDAP: Group {}.", group);
	}

	public void addMemberToGroup(Member member, Group group) throws InternalErrorException {
		//Add member to group
		Attribute uniqueMember = new BasicAttribute(PerunAttributeNames.ldapAttrUniqueMember, PerunAttributeNames.ldapAttrPerunUserId + "=" + member.getUserId() + "," + PerunAttributeNames.organizationalUnitPeople + "," + ldapProperties.getLdapBase());
		ModificationItem uniqueMemberItem = new ModificationItem(DirContext.ADD_ATTRIBUTE, uniqueMember);
		this.updateGroup(group, new ModificationItem[] {uniqueMemberItem});
		//Add member to vo if this group is memebrsGroup
		if(group.getName().equals(VosManager.MEMBERS_GROUP) && group.getParentGroupId() == null) {
			//Add info to vo
			vo.updateVo(group.getVoId(), new ModificationItem[] {uniqueMemberItem});
			//Add info also to user
			Attribute memberOfPerunVo = new BasicAttribute(PerunAttributeNames.ldapAttrMemberOfPerunVo, String.valueOf(group.getVoId()));
			ModificationItem memberOfPerunVoItem = new ModificationItem(DirContext.ADD_ATTRIBUTE, memberOfPerunVo);
			user.updateUserWithUserId(String.valueOf(member.getUserId()), new ModificationItem[] {memberOfPerunVoItem});
		}
		//Add group info to member
		Attribute memberOf = new BasicAttribute("memberOf", PerunAttributeNames.ldapAttrPerunGroupId + "=" + group.getId() + "," + PerunAttributeNames.ldapAttrPerunVoId + "=" + group.getVoId() + "," + ldapProperties.getLdapBase());
		ModificationItem memberOfItem = new ModificationItem(DirContext.ADD_ATTRIBUTE, memberOf);
		user.updateUserWithUserId(String.valueOf(member.getUserId()), new ModificationItem[] {memberOfItem});
	}

	public void removeMemberFromGroup(Member member, Group group) throws InternalErrorException {
		//Remove member from group
		Attribute uniqueMember = new BasicAttribute(PerunAttributeNames.ldapAttrUniqueMember, PerunAttributeNames.ldapAttrPerunUserId + "=" + member.getUserId() + "," + PerunAttributeNames.organizationalUnitPeople + "," + ldapProperties.getLdapBase());
		ModificationItem uniqueMemberItem = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, uniqueMember);
		this.updateGroup(group, new ModificationItem[] {uniqueMemberItem});
		//Remove member from vo if this group is membersGroup
		if(group.getName().equals(VosManager.MEMBERS_GROUP) && group.getParentGroupId() == null) {
			//Remove info from vo
			vo.updateVo(group.getVoId(), new ModificationItem[] {uniqueMemberItem});
			//Remove also information from user
			Attribute memberOfPerunVo = new BasicAttribute(PerunAttributeNames.ldapAttrMemberOfPerunVo, String.valueOf(group.getVoId()));
			ModificationItem memberOfPerunVoItem = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, memberOfPerunVo);
			user.updateUserWithUserId(String.valueOf(member.getUserId()), new ModificationItem[] {memberOfPerunVoItem});
		}
		//Remove group info from member
		Attribute memberOf = new BasicAttribute(PerunAttributeNames.ldapAttrMemberOf, PerunAttributeNames.ldapAttrPerunGroupId + "=" + group.getId() + "," + PerunAttributeNames.ldapAttrPerunVoId + "=" + group.getVoId() + "," + ldapProperties.getLdapBase());
		ModificationItem memberOfItem = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, memberOf);
		user.updateUserWithUserId(String.valueOf(member.getUserId()), new ModificationItem[] {memberOfItem});
	}

	public boolean isAlreadyMember(Member member, Group group) {
		Object o = ldapTemplate.lookup(getUserDN(String.valueOf(member.getUserId())), new UserMemberOfContextMapper());
		String[] memberOfInformation = (String []) o;
		if(memberOfInformation != null) {
			for(String s: memberOfInformation) {
				if(s.equals(PerunAttributeNames.ldapAttrPerunGroupId + "=" + group.getId() + "," + PerunAttributeNames.ldapAttrPerunVoId + "=" + group.getVoId() + "," + ldapProperties.getLdapBase())) return true;
			}
		}
		return false;
	}

	public boolean groupAttributeExist(Group group, String ldapAttributeName) throws InternalErrorException {
		Object o = null;
		try {
			setLdapAttributeName(ldapAttributeName);
			o = ldapTemplate.lookup(getGroupDN(String.valueOf(group.getVoId()), String.valueOf(group.getId())), new AttributeContextMapper());
		} catch (NameNotFoundException ex) {
			return false;
		}
		if(o == null) return false;
		return true;
	}

	public List<String> getAllUniqueMembersInGroup(int groupId, int voId) {
		List<String> uniqueMembers = new ArrayList<String>();
		Object o = ldapTemplate.lookup(getGroupDN(String.valueOf(voId), String.valueOf(groupId)), new GroupUniqueMemberOfContextMapper());
		String[] uniqueGroupInformation = (String []) o;
		if(uniqueGroupInformation != null) {
			for(String s: uniqueGroupInformation) {
				Matcher userIdMatcher = userIdPattern.matcher(s);
				if(userIdMatcher.find()) uniqueMembers.add(s.substring(userIdMatcher.start(), userIdMatcher.end()));
			}
		}
		return uniqueMembers;
	}

	private void mapToContext(Group group, DirContextOperations context) throws InternalErrorException {
		context.setAttributeValues("objectclass", new String[] {  
					PerunAttributeNames.objectClassTop,
					PerunAttributeNames.objectClassPerunGroup });
		context.setAttributeValue(PerunAttributeNames.ldapAttrCommonName, group.getName());
		context.setAttributeValue(PerunAttributeNames.ldapAttrPerunGroupId, String.valueOf(group.getId()));
		context.setAttributeValue(PerunAttributeNames.ldapAttrPerunUniqueGroupName, new String(vo.getVoShortName(group.getVoId()) + ":" + group.getName()));
		context.setAttributeValue(PerunAttributeNames.ldapAttrPerunVoId, String.valueOf(group.getVoId()));
		if(group.getDescription() != null && !group.getDescription().isEmpty()) 
			context.setAttributeValue(PerunAttributeNames.ldapAttrDescription, group.getDescription());
		if(group.getParentGroupId() != null) {
			context.setAttributeValue(PerunAttributeNames.ldapAttrPerunParentGroup, 
					PerunAttributeNames.ldapAttrPerunGroupId + "=" + group.getParentGroupId().toString() + "," + PerunAttributeNames.ldapAttrPerunVoId + "=" + group.getVoId() + "," + ldapProperties.getLdapBase());
			context.setAttributeValue(PerunAttributeNames.ldapAttrPerunParentGroupId, group.getParentGroupId().toString());
		}
	}
	
	private void mapToContext(DirContextOperations context, Map<String, String> values)  {
		for(Entry<String, String> value: values.entrySet()) {
			context.setAttributeValue(value.getKey(), value.getValue());
		}
	}
}
