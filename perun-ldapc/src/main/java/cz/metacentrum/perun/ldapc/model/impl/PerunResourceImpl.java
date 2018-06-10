package cz.metacentrum.perun.ldapc.model.impl;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.ModificationItem;

import org.apache.uima.resource.Resource_ImplBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.NameNotFoundException;

import cz.metacentrum.perun.core.api.Perun;
import cz.metacentrum.perun.core.api.Resource;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunResource;

public class PerunResourceImpl extends AbstractPerunEntry implements PerunResource {

	private final static Logger log = LoggerFactory.getLogger(PerunResourceImpl.class);

	public void createResource(Resource resource, String entityID) throws InternalErrorException {
		// Create a set of attributes
		Attributes attributes = new BasicAttributes();

		// Create the objectclass to add
		Attribute objClasses = new BasicAttribute(PerunAttributeNames.ldapAttrObjectClass);
		objClasses.add(PerunAttributeNames.objectClassTop);
		objClasses.add(PerunAttributeNames.objectClassPerunResource);

		// Add attributes
		attributes.put(objClasses);
		attributes.put(PerunAttributeNames.ldapAttrCommonName, resource.getName());
		attributes.put(PerunAttributeNames.ldapAttrPerunResourceId, String.valueOf(resource.getId()));
		attributes.put(PerunAttributeNames.ldapAttrPerunFacilityId, String.valueOf(resource.getFacilityId()));
		attributes.put(PerunAttributeNames.ldapAttrPerunVoId, String.valueOf(resource.getVoId()));
		if(resource.getDescription() != null && !resource.getDescription().isEmpty()) attributes.put(PerunAttributeNames.ldapAttrDescription, resource.getDescription());

		// get info about entityID attribute if exists
		if(entityID != null) attributes.put(PerunAttributeNames.ldapAttrEntityID, entityID);

		// Create the entry
		try {
			ldapTemplate.bind(getResourceDN(String.valueOf(resource.getVoId()), String.valueOf(resource.getId())), null, attributes);
			log.debug("New entry created in LDAP: Resource {} in Vo with Id=" + resource.getVoId() + " and Facility with ID=" + resource.getFacilityId() + ".", resource);
		} catch (NameNotFoundException e) {
			throw new InternalErrorException(e);
		}
	}

	public void deleteResource(Resource resource) throws InternalErrorException {
		try {
			ldapTemplate.unbind(getResourceDN(String.valueOf(resource.getVoId()), String.valueOf(resource.getId())));
			log.debug("Entry deleted from LDAP: Resource {} from Vo with ID=" + resource.getVoId() + " and Facility with ID=" + resource.getFacilityId() + ".", resource);
		} catch (NameNotFoundException e) {
			throw new InternalErrorException(e);
		}
	}

	public void updateResource(Resource resource, ModificationItem[] modificationItems) {
		ldapTemplate.modifyAttributes(getResourceDN(String.valueOf(resource.getVoId()), String.valueOf(resource.getId())), modificationItems);
		log.debug("Entry modified in LDAP: Resource {}.", resource);
	}

	public boolean resourceAttributeExist(Resource resource, String ldapAttributeName) throws InternalErrorException {
		if(ldapAttributeName == null) throw new InternalErrorException("ldapAttributeName can't be null.");
		Object o = null;
		try {
			setLdapAttributeName(ldapAttributeName);
			o = ldapTemplate.lookup(getResourceDN(String.valueOf(resource.getVoId()), String.valueOf(resource.getId())), new AttributeContextMapper());
		} catch (NameNotFoundException ex) {
			return false;
		}
		if(o == null) return false;
		return true;
	}

	
}
