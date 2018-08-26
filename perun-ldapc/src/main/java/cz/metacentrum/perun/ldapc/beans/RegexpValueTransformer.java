package cz.metacentrum.perun.ldapc.beans;

import java.util.List;

import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.ldapc.model.AttributeValueTransformer;

public class RegexpValueTransformer implements AttributeValueTransformer {

	public class RegexpSubst {

		private String find;
		private String replace;

		public String getFind() {
			return find;
		}
		public void setFind(String find) {
			this.find = find;
		}
		public String getReplace() {
			return replace;
		}
		public void setReplace(String replace) {
			this.replace = replace;
		}
	}
	
	private List<RegexpSubst> replaceList;

	public List<RegexpSubst> getReplaceList() {
		return replaceList;
	}

	public void setReplaceList(List<RegexpSubst> replaceList) {
		this.replaceList = replaceList;
	}

	@Override
	public String getValue(String value, Attribute attr) {
		String result = value;
		for (RegexpSubst regexpSubst : replaceList) {
			result.replaceAll(regexpSubst.getFind(), regexpSubst.getReplace());
		}
		return result;
	}

}
