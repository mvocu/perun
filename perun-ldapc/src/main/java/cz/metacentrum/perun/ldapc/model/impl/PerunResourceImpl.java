package cz.metacentrum.perun.ldapc.model.impl;

import java.util.Arrays;

import javax.naming.Name;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.support.LdapNameBuilder;

import cz.metacentrum.perun.core.api.Resource;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunAttribute;
import cz.metacentrum.perun.ldapc.model.PerunResource;

public class PerunResourceImpl extends AbstractPerunEntry<Resource> implements PerunResource {

	private final static Logger log = LoggerFactory.getLogger(PerunResourceImpl.class);

	private Iterable<PerunAttribute<Resource>> defaultResourceAttributes = Arrays.asList(
			new PerunAttributeDesc<>(
					PerunAttribute.PerunAttributeNames.ldapAttrCommonName, 
					PerunAttribute.REQUIRED, 
					(PerunAttributeDesc.SingleValueExtractor<Resource>)resource -> resource.getName()
					),
			new PerunAttributeDesc<>(
					PerunAttribute.PerunAttributeNames.ldapAttrPerunResourceId, 
					PerunAttribute.REQUIRED, 
					(PerunAttributeDesc.SingleValueExtractor<Resource>)resource -> resource.getId()
					),
			new PerunAttributeDesc<>(
					PerunAttribute.PerunAttributeNames.ldapAttrPerunFacilityId, 
					PerunAttribute.REQUIRED, 
					(PerunAttributeDesc.SingleValueExtractor<Resource>)resource -> resource.getFacilityId()
					),
			new PerunAttributeDesc<>(
					PerunAttribute.PerunAttributeNames.ldapAttrPerunVoId, 
					PerunAttribute.REQUIRED, 
					(PerunAttributeDesc.SingleValueExtractor<Resource>)resource -> resource.getVoId()
					),
			new PerunAttributeDesc<>(
					PerunAttribute.PerunAttributeNames.ldapAttrDescription, 
					PerunAttribute.OPTIONAL, 
					(PerunAttributeDesc.SingleValueExtractor<Resource>)resource -> resource.getDescription()
					)
			);
		
	public void addResource(Resource resource, String entityID) throws InternalErrorException {
		DirContextAdapter context = new DirContextAdapter(buildDN(resource));
		mapToContext(resource, context);
		// get info about entityID attribute if exists
		if(entityID != null) 
			context.setAttributeValue(PerunAttribute.PerunAttributeNames.ldapAttrEntityID, entityID);
		try {
			ldapTemplate.bind(context);
		} catch (NameNotFoundException e) {
			throw new InternalErrorException(e);
		}
	}

	public void deleteResource(Resource resource) throws InternalErrorException {
		deleteEntry(resource);
	}


	@Override
	public void updateResource(Resource resource) throws InternalErrorException {
		modifyEntry(resource, defaultResourceAttributes, 
				PerunAttribute.PerunAttributeNames.ldapAttrCommonName,
				PerunAttribute.PerunAttributeNames.ldapAttrDescription);
	}

	@Override
	public Name getEntryDN(String... id) {
		return LdapNameBuilder.newInstance(getBaseDN())
				.add(PerunAttribute.PerunAttributeNames.ldapAttrPerunVoId, id[0])
				.add(PerunAttribute.PerunAttributeNames.ldapAttrPerunResourceId, id[1])
				.build();
	}

	@Override
	protected Name buildDN(Resource bean) {
		return getEntryDN(String.valueOf(bean.getVoId()), String.valueOf(bean.getId()));
	}

	@Override
	protected void mapToContext(Resource bean, DirContextOperations context) throws InternalErrorException {
		context.setAttributeValue("objectclass", PerunAttribute.PerunAttributeNames.objectClassPerunResource);
		mapToContext(bean, context, defaultResourceAttributes);
	}

	
}
