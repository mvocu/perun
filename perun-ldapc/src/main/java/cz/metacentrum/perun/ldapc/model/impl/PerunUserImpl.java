package cz.metacentrum.perun.ldapc.model.impl;

import java.util.Arrays;
import java.util.List;

import javax.naming.Name;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.support.LdapNameBuilder;

import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunAttribute;
import cz.metacentrum.perun.ldapc.model.PerunUser;

public class PerunUserImpl extends AbstractPerunEntry<User> implements PerunUser {

	private final static Logger log = LoggerFactory.getLogger(PerunUserImpl.class);

	@Override
	protected List<String> getDefaultUpdatableAttributes() {
		return Arrays.asList(
				PerunAttribute.PerunAttributeNames.ldapAttrSurname,
				PerunAttribute.PerunAttributeNames.ldapAttrCommonName,
				PerunAttribute.PerunAttributeNames.ldapAttrGivenName);
	}

	@Override
	protected List<PerunAttribute<User>> getDefaultAttributeDescriptions() {
		return Arrays.asList(
				new PerunAttributeDesc<>(
						PerunAttribute.PerunAttributeNames.ldapAttrEntryStatus, 
						PerunAttribute.REQUIRED, 
						(PerunAttributeDesc.SingleValueExtractor<User>)user -> "active"
						),
				new PerunAttributeDesc<>(
						PerunAttribute.PerunAttributeNames.ldapAttrSurname, 
						PerunAttribute.REQUIRED, 
						(PerunAttributeDesc.SingleValueExtractor<User>)user -> user.getLastName()
						),
				new PerunAttributeDesc<>(
						PerunAttribute.PerunAttributeNames.ldapAttrGivenName, 
						PerunAttribute.OPTIONAL, 
						(PerunAttributeDesc.SingleValueExtractor<User>)user -> user.getFirstName()
						),
				new PerunAttributeDesc<>(
						PerunAttribute.PerunAttributeNames.ldapAttrCommonName, 
						PerunAttribute.REQUIRED, 
						(PerunAttributeDesc.SingleValueExtractor<User>)user -> {
							String firstName = user.getFirstName();
							String lastName = user.getLastName();
							String commonName = "";
							if(firstName == null || firstName.isEmpty()) firstName = "";
							else commonName+= firstName + " ";
							if(lastName == null || lastName.isEmpty()) lastName = "N/A";
							commonName+= lastName;
							return commonName;
						}
						),
				new PerunAttributeDesc<>(
						PerunAttribute.PerunAttributeNames.ldapAttrPerunUserId, 
						PerunAttribute.REQUIRED, 
						(PerunAttributeDesc.SingleValueExtractor<User>)user -> user.getId()
						),
				new PerunAttributeDesc<>(
						PerunAttribute.PerunAttributeNames.ldapAttrIsServiceUser, 
						PerunAttribute.REQUIRED, 
						(PerunAttributeDesc.SingleValueExtractor<User>)user -> user.isServiceUser() ? "1" : "0"
						),
				new PerunAttributeDesc<>(
						PerunAttribute.PerunAttributeNames.ldapAttrIsSponsoredUser, 
						PerunAttribute.REQUIRED, 
						(PerunAttributeDesc.SingleValueExtractor<User>)user -> user.isSponsoredUser() ? "1" : "0"
						),
				new PerunAttributeDesc<>(
						PerunAttribute.PerunAttributeNames.ldapAttrPreferredMail, 
						PerunAttribute.OPTIONAL, 
						(PerunAttributeDesc.SingleValueExtractor<User>)user -> user.isSponsoredUser() ? "1" : "0"
						)
				);
	}

	public void addUser(User user) throws InternalErrorException {
		addEntry(user);
	}

	public void deleteUser(User user) throws InternalErrorException {
		deleteEntry(user);
	}

	@Override
	public void updateUser(User user) throws InternalErrorException {
		modifyEntry(user);
	}

	public boolean userPasswordExists(User user) {
		return entryAttributeExists(user, PerunAttribute.PerunAttributeNames.ldapAttrUserPassword);
	}

	@Override
	protected Name buildDN(User bean) {
		return getEntryDN(String.valueOf(bean.getId()));
	}

	@Override
	protected void mapToContext(User bean, DirContextOperations context) throws InternalErrorException {
		context.setAttributeValues(PerunAttribute.PerunAttributeNames.ldapAttrObjectClass,
				Arrays.asList(PerunAttribute.PerunAttributeNames.objectClassPerson,
						PerunAttribute.PerunAttributeNames.objectClassOrganizationalPerson,
						PerunAttribute.PerunAttributeNames.objectClassInetOrgPerson,
						PerunAttribute.PerunAttributeNames.objectClassPerunUser,
						PerunAttribute.PerunAttributeNames.objectClassTenOperEntry,
						PerunAttribute.PerunAttributeNames.objectClassInetUser).toArray());
		mapToContext(bean, context, getAttributeDescriptions());
	}

	/**
	 * Get User DN using user id.
	 *
	 * @param userId user id
	 * @return DN in Name
	 */
	@Override
	public Name getEntryDN(String ...userId) {
		return LdapNameBuilder.newInstance(getBaseDN())
				.add(PerunAttribute.PerunAttributeNames.organizationalUnitPeople)
				.add(PerunAttribute.PerunAttributeNames.ldapAttrPerunUserId, userId[0])
				.build();
	}

}
