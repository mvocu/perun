package cz.metacentrum.perun.ldapc.model;

import java.util.List;

import javax.naming.Name;

import org.springframework.ldap.core.DirContextOperations;

import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;

public interface PerunEntry<T extends PerunBean> {

	void addEntry(T bean) throws InternalErrorException;

	void modifyEntry(T bean) throws InternalErrorException;

	void modifyEntry(T bean, String... attrNames) throws InternalErrorException;

	void modifyEntry(T bean, Iterable<PerunAttribute<T>> attrs, String... attrNames) throws InternalErrorException;

	void deleteEntry(T bean) throws InternalErrorException;

	DirContextOperations findByDN(Name dn);
	
	DirContextOperations findById(String ...id);

	Name getEntryDN(String ...id);
	
	Boolean entryExists(T bean);
	
	/**
	 * Return true if entry attribute with ldapAttributeName in ldap exists.
	 *
	 * @param bean bean of entry in perun
	 * @param ldapAttributeName name of user ldap attribute
	 * @return true if attribute in ldap exists, false if not
	 * @throws InternalErrorException if ldapAttributeName is null
	 */
	Boolean entryAttributeExists(T bean, String ldapAttributeName);

	List<PerunAttribute<T>> getAttributeDescriptions();

	void setAttributeDescriptions(List<PerunAttribute<T>> attributeDescriptions);

	List<String> getUpdatableAttributeNames();

	void setUpdatableAttributeNames(List<String> updatableAttributeNames);
}
