package cz.metacentrum.perun.ldapc.processor.impl;

import java.util.regex.Pattern;

import cz.metacentrum.perun.ldapc.processor.EventDispatcher.MessageBeans;

public class UserAttributeProcessor extends AbstractEventProcessor {

	private Pattern userSetPattern = Pattern.compile(" set for User:\\[(.*)\\]");
	private Pattern userRemovePattern = Pattern.compile(" removed for User:\\[(.*)\\]");
	private Pattern userAllAttrsRemovedPattern = Pattern.compile("All attributes removed for User:\\[(.*)\\]");

	private Pattern userUidNamespacePattern = Pattern.compile(cz.metacentrum.perun.core.api.AttributesManager.NS_USER_ATTR_DEF + ":uid-namespace:");
	private Pattern userLoginNamespacePattern = Pattern.compile(cz.metacentrum.perun.core.api.AttributesManager.NS_USER_ATTR_DEF + ":login-namespace:");

	//UserExtSources patterns
	private Pattern addUserExtSourcePattern = Pattern.compile("UserExtSource:\\[(.*)\\] added to User:\\[(.*)\\]");
	private Pattern removeUserExtSourcePattern = Pattern.compile("UserExtSource:\\[(.*)\\] removed from User:\\[(.*)\\]");
	
	
	@Override
	public void processEvent(String msg, MessageBeans beans) {
		
	}

}
