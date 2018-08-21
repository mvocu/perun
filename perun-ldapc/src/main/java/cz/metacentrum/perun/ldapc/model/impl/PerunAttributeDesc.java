package cz.metacentrum.perun.ldapc.model.impl;

import static org.hamcrest.CoreMatchers.instanceOf;

import java.util.ArrayList;
import java.util.Arrays;

import com.mysql.fabric.xmlrpc.base.Array;

import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunAttribute;

public class PerunAttributeDesc<T extends PerunBean> implements PerunAttribute<T> {

	public interface SingleValueExtractor<T extends PerunBean> {
		public Object getValue(T bean) throws InternalErrorException;
	}

	public interface MultipleValuesExtractor<T extends PerunBean> {
		public Object[] getValues(T bean) throws InternalErrorException;
	}
	
	public interface AttributeSingleValueExtractor {
		public Object getValue(Attribute attr) throws InternalErrorException;
	}
	
	public interface AttributeMultipleValuesExtractor {
		public Object[] getValues(Attribute attr) throws InternalErrorException;
	}

	public PerunAttributeDesc(String name, Boolean required, SingleValueExtractor<T> extractor) {
		super();
		this.name = name;
		this.required = required;
		this.multivalued = false;
		this.singleValueExtractor = extractor;
	}

	public PerunAttributeDesc(String name, Boolean required, MultipleValuesExtractor<T> extractor) {
		super();
		this.name = name;
		this.required = required;
		this.multivalued = true;
		this.multipleValuesExtractor = extractor;
	}
	
	public PerunAttributeDesc(String name, Boolean required, AttributeSingleValueExtractor extractor) {
		super();
		this.name = name;
		this.required = required;
		this.multivalued = false;
		this.attrSingleValueExtractor = extractor;
	}

	public PerunAttributeDesc(String name, Boolean required, AttributeMultipleValuesExtractor extractor) {
		super();
		this.name = name;
		this.required = required;
		this.multivalued = true;
		this.attrMultipleValuesExtractor = extractor;
	}

	@Override
	public boolean isRequired() {
		return getRequired();
	}

	@Override
	public boolean isMultiValued() {
		return getMultivalued();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean hasValue(T bean) throws InternalErrorException {
		Object value = this.getValue(bean);
		return value != null && !String.valueOf(value).isEmpty();
	}

	@Override
	public Object getValue(T bean) throws InternalErrorException {
		return singleValueExtractor != null ? singleValueExtractor.getValue(bean) : null;
	}

	@Override
	public Object[] getValues(T bean) throws InternalErrorException {
		return multipleValuesExtractor != null ? multipleValuesExtractor.getValues(bean) : null;
	}

	@Override
	public boolean hasValue(Attribute attr) throws InternalErrorException {
		Object value = attr.getValue();
		return value != null && !String.valueOf(value).isEmpty();
	}

	@Override
	public Object getValue(Attribute attr) throws InternalErrorException {
		return attr.getValue();
	}

	@Override
	public Object[] getValues(Attribute attr) throws InternalErrorException {
		Object value = attr.getValue();
		if(value instanceof ArrayList<?>) {
			return ((ArrayList)value).toArray();
		} else {
			return null;
		}
	}

	public void setName(String name) {
		this.name = name;
	}

	public Boolean getRequired() {
		return required;
	}

	public void setRequired(Boolean required) {
		this.required = required;
	}

	public Boolean getMultivalued() {
		return multivalued;
	}

	public SingleValueExtractor<T> getSingleValueExtractor() {
		return singleValueExtractor;
	}

	public void setSingleValueExtractor(SingleValueExtractor<T> valueExtractor) {
		this.multivalued = false;
		this.singleValueExtractor = valueExtractor;
	}

	private String name;
	private Boolean required;
	private Boolean multivalued;
	private SingleValueExtractor<T> singleValueExtractor;
	private MultipleValuesExtractor<T> multipleValuesExtractor;
	private AttributeSingleValueExtractor attrSingleValueExtractor;
	private AttributeMultipleValuesExtractor attrMultipleValuesExtractor;
	
}
