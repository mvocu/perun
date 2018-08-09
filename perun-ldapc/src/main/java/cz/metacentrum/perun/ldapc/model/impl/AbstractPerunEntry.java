package cz.metacentrum.perun.ldapc.model.impl;


import java.util.ArrayList;
import java.util.List;

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
	public void modifyEntry(T bean, Iterable<PerunAttribute<T>> attrs, String...attrNames) throws InternalErrorException {
		DirContextAdapter contextAdapter = new DirContextAdapter(buildDN(bean));
		mapToContext(bean, contextAdapter, getAttributeDefs(attrs, attrNames));
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

	protected Iterable<PerunAttribute<T>> getAttributeDefs(Iterable<PerunAttribute<T>> attrs, String[] attrNames) {
		List<PerunAttribute<T>> result = new ArrayList<PerunAttribute<T>>();
		for(PerunAttribute<T> attrDesc : attrs) {
			for(String attrName : attrNames) {
				if(attrDesc.getName().equals(attrName)) result.add(attrDesc); 
			}
		}
		return result;
	}
} 
