package cz.metacentrum.perun.ldapc.model.impl;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.naming.Name;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;

import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.AttributeDefinition;
import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.beans.LdapProperties;
import cz.metacentrum.perun.ldapc.model.PerunAttribute;
import cz.metacentrum.perun.ldapc.model.PerunEntry;

public abstract class AbstractPerunEntry<T extends PerunBean> implements InitializingBean, PerunEntry<T> {

	@Autowired
	protected LdapTemplate ldapTemplate;
	@Autowired
	protected LdapProperties ldapProperties;

	private List<PerunAttribute<T>> attributeDescriptions;
	private List<String> updatableAttributeNames; 
	
	public void afterPropertiesSet() {
		if(attributeDescriptions == null)
			attributeDescriptions = getDefaultAttributeDescriptions();
		else
			attributeDescriptions.addAll(getDefaultAttributeDescriptions());
		if(updatableAttributeNames == null)
			updatableAttributeNames = getDefaultUpdatableAttributes();
		else
			updatableAttributeNames.addAll(getDefaultUpdatableAttributes());
			
	}

	abstract protected List<String> getDefaultUpdatableAttributes();

	abstract protected List<PerunAttribute<T>> getDefaultAttributeDescriptions();

	/* (non-Javadoc)
	 * @see cz.metacentrum.perun.ldapc.model.impl.PerunEntry#addEntry(cz.metacentrum.perun.core.api.PerunBean)
	 */
	@Override
	public void addEntry(T bean) throws InternalErrorException {
		DirContextAdapter context = new DirContextAdapter(buildDN(bean));
		mapToContext(bean, context);
		ldapTemplate.bind(context);
	}
	
	@Override
	public void modifyEntry(T bean) throws InternalErrorException {
		modifyEntry(bean, attributeDescriptions, updatableAttributeNames);
	}

	@Override
	public void modifyEntry(T bean, String... attrNames) throws InternalErrorException {
		modifyEntry(bean, attributeDescriptions, Arrays.asList(attrNames));
	}

	/* (non-Javadoc)
	 * @see cz.metacentrum.perun.ldapc.model.impl.PerunEntry#modifyEntry(cz.metacentrum.perun.core.api.PerunBean)
	 */
	@Override
	public void modifyEntry(T bean, Iterable<PerunAttribute<T>> attrs, String...attrNames) throws InternalErrorException {
		modifyEntry(bean, attrs, Arrays.asList(attrNames));
	}
	
	protected void modifyEntry(T bean, Iterable<PerunAttribute<T>> attrs, List<String> attrNames) throws InternalErrorException {
		DirContextAdapter contextAdapter = new DirContextAdapter(buildDN(bean));
		mapToContext(bean, contextAdapter, findAttributeDescriptions(attrs, attrNames));
		ldapTemplate.modifyAttributes(contextAdapter);
	}

	@Override
	public void modifyEntry(T bean, AttributeDefinition attr) throws InternalErrorException {
		PerunAttribute<T> attrDef = findAttributeDescription(getAttributeDescriptions(), attr);
		if(attrDef != null) {
			modifyEntry(bean, attrDef, attr);
		} else 
			throw new InternalErrorException("Attribute description for attribute " + attr.getName() + " not found");
	}

	@Override
	public void modifyEntry(T bean, PerunAttribute<T> attrDef, AttributeDefinition attr) throws InternalErrorException {
		DirContextAdapter contextAdapter = new DirContextAdapter(buildDN(bean));
		mapToContext(bean, contextAdapter, attrDef, attr);
		ldapTemplate.modifyAttributes(contextAdapter);
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
	
	@Override
	public List<PerunAttribute<T>> getAttributeDescriptions() {
		return attributeDescriptions;
	}

	@Override
	public void setAttributeDescriptions(List<PerunAttribute<T>> attributeDescriptions) {
		this.attributeDescriptions = attributeDescriptions;
	}

	@Override
	public List<String> getUpdatableAttributeNames() {
		return updatableAttributeNames;
	}

	@Override
	public void setUpdatableAttributeNames(List<String> updatableAttributeNames) {
		this.updatableAttributeNames = updatableAttributeNames;
	}

	protected String getBaseDN() {
		return ldapProperties.getLdapBase();
	}
	
	abstract protected Name buildDN(T bean);
	
	abstract protected void mapToContext(T bean, DirContextOperations context) throws InternalErrorException;
	
	/**
	 * Takes data from Perun bean and stores them into LDAP entry (context) for creation or update.
	 * List of attributes to fill-in is given as parameter; if attribute has no value, it will be removed.
	 * Attribute definitions that require data from Attribute bean are ignored.
	 * 
	 * @param bean - Perun bean containing the basic data
	 * @param context - LDAP context (ie. entry) that should be filled
	 * @param attrs - list of known attributes
	 * @throws InternalErrorException
	 */
	protected void mapToContext(T bean, DirContextOperations context, Iterable<PerunAttribute<T>> attrs) throws InternalErrorException {
		for(PerunAttribute<T> attr: attrs) {
			if(attr.requiresAttributeBean())
				continue;
			Object[] values;
			if(attr.isMultiValued()) {
				values = attr.getValues(bean);
			} else {
				if(attr.hasValue(bean)) {
					values = Arrays.asList(attr.getValue(bean)).toArray();
				} else {
					values = null;
				}
			} 
			context.setAttributeValues(attr.getName(), values);
		}
	}

	/**
	 * 
	 * 
	 * @param bean
	 * @param entry
	 * @param attrDef
	 * @param attr
	 */
	protected void mapToContext(T bean, DirContextOperations entry, PerunAttribute<T> attrDef, AttributeDefinition attr) throws InternalErrorException {
		Object[] values;
		if(attr instanceof Attribute) {
			if(attrDef.isMultiValued()) {
				values = attrDef.getValues(bean, (Attribute)attr);
			} else {
				if(attrDef.hasValue(bean, (Attribute)attr)) {
				values = Arrays.asList(attrDef.getValue(bean, (Attribute)attr)).toArray();
				} else {
					values = null;
				}
			}
		} else {
			values = null;
		}
		entry.setAttributeValues(attrDef.getName(attr), values);
	}
	
	protected Iterable<PerunAttribute<T>> findAttributeDescriptions(Iterable<PerunAttribute<T>> attrs, Iterable<String> attrNames) {
		List<PerunAttribute<T>> result = new ArrayList<PerunAttribute<T>>();
		/* 
		 * attribute name given may be just prefix for the actual attribute name, e.g. for login;x-ns- 
		 * where the last part is given by the actual attribute name parameter; 
		 * we use the baseName for comparison
		 */
		for(PerunAttribute<T> attrDesc : attrs) {
			for(String attrName : attrNames) {
				if(attrDesc.getBaseName().equals(attrName)) result.add(attrDesc); 
			}
		}
		return result;
	}

	protected PerunAttribute<T> findAttributeDescription(List<PerunAttribute<T>> attrs, AttributeDefinition attr) {
		PerunAttribute<T> result = null;
		for (PerunAttribute<T> attrDef : attrs) {
			AttributeValueExtractor extractor = null;
			if(attrDef.isMultiValued()) {
				PerunAttribute.MultipleValuesExtractor<T> ext = attrDef.getMultipleValuesExtractor();
				if(ext instanceof AttributeValueExtractor) {
						extractor = (AttributeValueExtractor)ext;
				}
			} else {
				PerunAttribute.SingleValueExtractor<T> ext = attrDef.getSingleValueExtractor();
				if(ext instanceof AttributeValueExtractor) {
					extractor = (AttributeValueExtractor)ext;
				}
			}
			if(extractor != null && 
					attr.getBaseFriendlyName().equals(extractor.getName()) &&
					attr.getNamespace().equals(extractor.getNamespace())) {
				result = attrDef;
				break;
			}
		}
		return result;
	}

} 
