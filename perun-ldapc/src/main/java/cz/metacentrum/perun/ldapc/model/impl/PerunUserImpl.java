package cz.metacentrum.perun.ldapc.model.impl;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.ModificationItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.DirContextOperations;

import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Resource;
import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunUser;

public class PerunUserImpl extends AbstractPerunEntry implements PerunUser {

	private final static Logger log = LoggerFactory.getLogger(PerunUserImpl.class);

	public void createUser(User user) throws InternalErrorException {
		// Create a set of attributes
		Attributes attributes = new BasicAttributes();

		// Create the objectclass to add
		Attribute objClasses = new BasicAttribute(PerunAttributeNames.ldapAttrObjectClass);
		objClasses.add(PerunAttributeNames.objectClassTop);
		objClasses.add(PerunAttributeNames.objectClassPerson);
		objClasses.add(PerunAttributeNames.objectClassOrganizationalPerson);
		objClasses.add(PerunAttributeNames.objectClassInetOrgPerson);
		objClasses.add(PerunAttributeNames.objectClassPerunUser);
		objClasses.add(PerunAttributeNames.objectClassTenOperEntry);
		objClasses.add(PerunAttributeNames.objectClassInetUser);

		String firstName = user.getFirstName();
		String lastName = user.getLastName();
		String commonName = "";
		if(firstName == null || firstName.isEmpty()) firstName = "";
		else commonName+= firstName + " ";
		if(lastName == null || lastName.isEmpty()) lastName = "N/A";
		commonName+= lastName;

		// Add attributes
		attributes.put(objClasses);
		attributes.put(PerunAttributeNames.ldapAttrEntryStatus, "active");
		attributes.put(PerunAttributeNames.ldapAttrSurname, lastName);
		attributes.put(PerunAttributeNames.ldapAttrCommonName, commonName);
		if(!firstName.isEmpty()) attributes.put(PerunAttributeNames.ldapAttrGivenName, firstName);
		attributes.put(PerunAttributeNames.ldapAttrPerunUserId, String.valueOf(user.getId()));
		if(user.isServiceUser()) attributes.put(PerunAttributeNames.ldapAttrIsServiceUser, "1");
		else attributes.put(PerunAttributeNames.ldapAttrIsServiceUser, "0");
		if(user.isSponsoredUser()) attributes.put(PerunAttributeNames.ldapAttrIsSponsoredUser, "1");
		else attributes.put(PerunAttributeNames.ldapAttrIsSponsoredUser, "0");

		// Create the entry
		try {
			ldapTemplate.bind(getUserDN(String.valueOf(user.getId())), null, attributes);
			log.debug("New entry created in LDAP: User {} in Group with Id=" + user.getId() + ".", user);
		} catch (NameNotFoundException e) {
			throw new InternalErrorException(e);
		}
	}

	public void deleteUser(User user) throws InternalErrorException {
		try {
			ldapTemplate.unbind(getUserDN(String.valueOf(user.getId())));
			log.debug("Entry deleted from LDAP: User {}.", user);
		} catch (NameNotFoundException e) {
			throw new InternalErrorException(e);
		}
	}

	public void updateUser(User user, ModificationItem[] modificationItems) {
		this.updateUserWithUserId(String.valueOf(user.getId()), modificationItems);
	}

	public void updateUsersAttributeInLDAP(String userId, String ldapAttrName, String[] records) {
		DirContextOperations context = ldapTemplate.lookupContext(getUserDN(userId));
		context.setAttributeValues(ldapAttrName, records);
		ldapTemplate.modifyAttributes(context);
		log.debug("Entry modified in LDAP: UserId {}.", userId);
	}

	public void updateUserWithUserId(String userId, ModificationItem[] modificationItems) {
		ldapTemplate.modifyAttributes(getUserDN(userId), modificationItems);
		log.debug("Entry modified in LDAP: UserId {}.", userId);
	}

	public Attributes getAllUsersAttributes(User user) {
		Object o = ldapTemplate.lookup(getUserDN(String.valueOf(user.getId())), new UserAttributesContextMapper());
		Attributes attrs = null;
		if(o != null) attrs = (Attributes) o;
		return attrs;
	}

	public boolean userExist(User user) {
		Object o = null;
		try {
			o = ldapTemplate.lookup(getUserDN(String.valueOf(user.getId())), new UserPerunUserIdContextMapper());
		} catch (NameNotFoundException ex) {
			return false;
		}
		return true;
	}

	public boolean userAttributeExist(User user, String ldapAttributeName) throws InternalErrorException {
		if(ldapAttributeName == null) throw new InternalErrorException("ldapAttributeName can't be null.");
		Object o = null;
		try {
			setLdapAttributeName(ldapAttributeName);
			o = ldapTemplate.lookup(getUserDN(String.valueOf(user.getId())), new AttributeContextMapper());
		} catch (NameNotFoundException ex) {
			return false;
		}
		if(o == null) return false;
		return true;
	}

	public boolean userPasswordExists(User user) {
		Object o = ldapTemplate.lookup(getUserDN(String.valueOf(user.getId())), new UserAttributesContextMapper());
		Attributes attrs = null;
		if(o != null) attrs = (Attributes) o;

		if(attrs != null) {
			Attribute a = attrs.get(PerunAttributeNames.ldapAttrUserPassword);
			if(a != null) return true;
		}
		return false;
	}

}
