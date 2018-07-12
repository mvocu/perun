package cz.metacentrum.perun.ldapc.model.impl;


import javax.naming.Name;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;

import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.beans.LdapProperties;
import cz.metacentrum.perun.ldapc.model.PerunAttribute;
import cz.metacentrum.perun.ldapc.model.PerunEntry;

public abstract class AbstractPerunEntry<T extends PerunBean> implements PerunEntry<T> {

	protected interface PerunAttributeNames {

		//PERUN ATTRIBUTES NAMES
		public static final String perunAttrPreferredMail = "preferredMail";
		public static final String perunAttrMail = "mail";
		public static final String perunAttrOrganization = "organization";
		public static final String perunAttrPhone = "phone";
		public static final String perunAttrUserCertDNs = "userCertDNs";
		public static final String perunAttrBonaFideStatus = "elixirBonaFideStatus";
		public static final String perunAttrSchacHomeOrganizations = "schacHomeOrganizations";
		public static final String perunAttrEduPersonScopedAffiliations = "eduPersonScopedAffiliations";
		public static final String perunAttrLibraryIDs = "libraryIDs";
		public static final String perunAttrEntityID = "entityID";
		public static final String perunAttrClientID = "OIDCClientID";
		public static final String perunAttrGroupNames = "groupNames";
		public static final String perunAttrInstitutionsCountries = "institutionsCountries";

		//LDAP ATTRIBUTES NAMES
		public static final String ldapAttrAssignedToResourceId = "assignedToResourceId";
		public static final String ldapAttrAssignedGroupId = "assignedGroupId";
		public static final String ldapAttrDescription = "description";
		public static final String ldapAttrCommonName = "cn";
		public static final String ldapAttrPerunUniqueGroupName= "perunUniqueGroupName";
		public static final String ldapAttrEduPersonPrincipalNames = "eduPersonPrincipalNames";
		public static final String ldapAttrPreferredMail = perunAttrPreferredMail;
		public static final String ldapAttrMail = perunAttrMail;
		public static final String ldapAttrOrganization = "o";
		public static final String ldapAttrTelephoneNumber = "telephoneNumber";
		public static final String ldapAttrUserCertDNs = "userCertificateSubject";
		public static final String ldapAttrBonaFideStatus = "bonaFideStatus";
		public static final String ldapAttrSchacHomeOrganizations = perunAttrSchacHomeOrganizations;
		public static final String ldapAttrEduPersonScopedAffiliations = perunAttrEduPersonScopedAffiliations;
		public static final String ldapAttrLibraryIDs = perunAttrLibraryIDs;
		public static final String ldapAttrUidNumber = "uidNumber;x-ns-";
		public static final String ldapAttrLogin = "login;x-ns-";
		public static final String ldapAttrUserPassword = "userPassword";
		public static final String ldapAttrSurname = "sn";
		public static final String ldapAttrGivenName = "givenName";
		public static final String ldapAttrEntityID = perunAttrEntityID;
		public static final String ldapAttrClientID = perunAttrClientID;
		public static final String ldapAttrObjectClass = "objectClass";
		public static final String ldapAttrPerunVoId = "perunVoId";
		public static final String ldapAttrPerunFacilityId = "perunFacilityId";
		public static final String ldapAttrPerunUserId = "perunUserId";
		public static final String ldapAttrPerunGroupId = "perunGroupId";
		public static final String ldapAttrPerunResourceId = "perunResourceId";
		public static final String ldapAttrPerunParentGroup = "perunParentGroup";
		public static final String ldapAttrPerunParentGroupId = "perunParentGroupId";
		public static final String ldapAttrMemberOf = "memberOf";
		public static final String ldapAttrUniqueMember = "uniqueMember";
		public static final String ldapAttrMemberOfPerunVo = "memberOfPerunVo";
		public static final String ldapAttrEntryStatus = "entryStatus";
		public static final String ldapAttrIsServiceUser = "isServiceUser";
		public static final String ldapAttrIsSponsoredUser = "isSponsoredUser";
		public static final String ldapAttrGroupNames = perunAttrGroupNames;
		public static final String ldapAttrInstitutionsCountries = perunAttrInstitutionsCountries;

		//LDAP OBJECT CLASSES
		public static final String objectClassTop = "top";
		public static final String objectClassPerunResource = "perunResource";
		public static final String objectClassPerunGroup = "perunGroup";
		public static final String objectClassOrganization = "organization";
		public static final String objectClassPerunVO = "perunVO";
		public static final String objectClassPerson = "person";
		public static final String objectClassOrganizationalPerson = "organizationalPerson";
		public static final String objectClassInetOrgPerson = "inetOrgPerson";
		public static final String objectClassPerunUser = "perunUser";
		public static final String objectClassTenOperEntry = "tenOperEntry";
		public static final String objectClassInetUser = "inetUser";

		//LDAP ORGANIZATION UNITS
		public static final String organizationalUnitPeople = "ou=People";

	}
	
	@Autowired
	protected LdapTemplate ldapTemplate;
	@Autowired
	protected LdapProperties ldapProperties;

	
	/* (non-Javadoc)
	 * @see cz.metacentrum.perun.ldapc.model.impl.PerunEntry#addEntry(cz.metacentrum.perun.core.api.PerunBean)
	 */
	@Override
	public void addEntry(T bean) throws InternalErrorException {
		DirContextAdapter context = new DirContextAdapter(buildDN(bean));
		mapToContext(bean, context);
		ldapTemplate.bind(context);
	}
	
	/* (non-Javadoc)
	 * @see cz.metacentrum.perun.ldapc.model.impl.PerunEntry#modifyEntry(cz.metacentrum.perun.core.api.PerunBean)
	 */
	@Override
	public void modifyEntry(T bean) throws InternalErrorException {
	
	}
	
	/* (non-Javadoc)
	 * @see cz.metacentrum.perun.ldapc.model.impl.PerunEntry#deleteEntry(cz.metacentrum.perun.core.api.PerunBean)
	 */
	@Override
	public void deleteEntry(T bean) throws InternalErrorException {
		try {
			ldapTemplate.unbind(buildDN(bean));
		} catch (NameNotFoundException e) {
			throw new InternalErrorException(e);
		}
	}
	

	@Override
	public DirContextOperations findByDN(Name dn) {
		return ldapTemplate.lookupContext(dn);
	}


	@Override
	public DirContextOperations findById(String ...id) {
		return ldapTemplate.lookupContext(getEntryDN(id));
	}

	abstract public Name getEntryDN(String ...id);
	
	@Override
	public Boolean entryAttributeExists(T bean, String ldapAttributeName) { 
		DirContextOperations entry = findByDN(buildDN(bean));
		String value = entry.getStringAttribute(ldapAttributeName);
		return (value != null);
	}

	@Override
	public Boolean entryExists(T bean) {
		try {
			DirContextOperations entry = findByDN(buildDN(bean));
		} catch (NameNotFoundException e) {
			return false;
		}
		return true; 
	}
	
	protected String getBaseDN() {
		return ldapProperties.getLdapBase();
	}
	
	abstract protected Name buildDN(T bean);
	
	abstract protected void mapToContext(T bean, DirContextOperations context) throws InternalErrorException;
	
	protected void mapToContext(T bean, DirContextOperations context, Iterable<PerunAttribute<T>> attrs) throws InternalErrorException {
		for(PerunAttribute<T> attr: attrs) {
			if(attr.isRequired() || attr.hasValue(bean)) {
				if(attr.isMultiValued()) {
					context.setAttributeValues(attr.getName(), attr.getValues(bean));
				} else {
					context.setAttributeValue(attr.getName(), attr.getValue(bean));
				}
			}
		}
	}


}
