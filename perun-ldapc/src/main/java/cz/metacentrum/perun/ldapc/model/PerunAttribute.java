package cz.metacentrum.perun.ldapc.model;

import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;

public interface PerunAttribute<T extends PerunBean> {

	public static final boolean REQUIRED = true;
	public static final boolean OPTIONAL = false;

	public static final boolean MULTIVALUED = true;
	public static final boolean SINGLE = false;
	
	public boolean isRequired();

	public boolean isMultiValued();

	public String getName();

	public boolean hasValue(T bean) throws InternalErrorException;

	public Object getValue(T bean) throws InternalErrorException;

	public Object[] getValues(T bean) throws InternalErrorException;

}
