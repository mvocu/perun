package cz.metacentrum.perun.ldapc.model.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.BeansUtils;
import cz.metacentrum.perun.core.api.PerunBean;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunAttribute.MultipleValuesExtractor;

public class MultipleAttributeValueExtractor<T extends PerunBean> extends AttributeValueExtractor implements MultipleValuesExtractor<T> {

	@Override
	public String[] getValues(T bean, Attribute... attributes) throws InternalErrorException {
		for (Attribute attribute : attributes) {
			if(this.appliesToAttribute(attribute)) {
				if (attribute.getType().equals(String.class.getName()) || attribute.getType().equals(BeansUtils.largeStringClassName)) {
					return new String[] { attribute.getValue().toString() };
				} else if (attribute.getType().equals(ArrayList.class.getName()) || attribute.getType().equals(BeansUtils.largeArrayListClassName)) {
					return attribute.valueAsList().toArray(new String[5]);
				} else if (attribute.getType().equals(LinkedHashMap.class.getName())) {
					return attribute.valueAsMap().entrySet().toArray(new String[5]);
				} else {
					return null;
				}
			}
		}
		return null;
	}

}
