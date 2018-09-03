package cz.metacentrum.perun.ldapc.processor.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.metacentrum.perun.core.api.ExtSourcesManager;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunUser;
import cz.metacentrum.perun.ldapc.processor.EventDispatcher.MessageBeans;

public class UserAttributeProcessor extends AbstractAttributeProcessor {

	private final static Logger log = LoggerFactory.getLogger(UserAttributeProcessor.class);

	@Autowired
	protected PerunUser perunUser;
	
	private static Pattern userSetPattern = Pattern.compile(" set for User:\\[(.*)\\]");
	private static Pattern userRemovePattern = Pattern.compile(" removed for User:\\[(.*)\\]");
	private static Pattern userAllAttrsRemovedPattern = Pattern.compile("All attributes removed for User:\\[(.*)\\]");

	//UserExtSources patterns
	private Pattern addUserExtSourcePattern = Pattern.compile("UserExtSource:\\[(.*)\\] added to User:\\[(.*)\\]");
	private Pattern removeUserExtSourcePattern = Pattern.compile("UserExtSource:\\[(.*)\\] removed from User:\\[(.*)\\]");
	
	
	public UserAttributeProcessor() {
		super(MessageBeans.USER_F, userSetPattern, userRemovePattern, userAllAttrsRemovedPattern);
	}
	
	
	public void processAttributeSet(String msg, MessageBeans beans) {
		// ensure we have the correct beans available
		if(beans.getAttribute() == null || beans.getUser() == null) {
			return;
		}
		try {
			perunUser.modifyEntry(beans.getUser(), beans.getAttribute());
		} catch (InternalErrorException e) {
		}
	}

	public void processAttributeRemoved(String msg, MessageBeans beans) {
		// ensure we have the correct beans available
		if(beans.getAttributeDef() == null || beans.getUser() == null) {
			return;
		}
		try {
			perunUser.modifyEntry(beans.getUser(), beans.getAttributeDef());
		} catch (InternalErrorException e) {
		}
	}

	public void processAllAttributesRemoved(String msg, MessageBeans beans) {
		// ensure we have the correct beans available
		if(beans.getUser() == null) {
			return;
		}
		perunUser.removeAllAttributes(beans.getUser());
	}	

	public void processExtSourceSet(String msg, MessageBeans beans) {
		// ensure we have the correct beans available
		if(beans.getUser() == null || beans.getUserExtSource() == null) {
			return;
		}
		try {
			if(beans.getUserExtSource().getExtSource().getType().equals(ExtSourcesManager.EXTSOURCE_IDP)) {
				perunUser.addPrincipal(beans.getUser(), beans.getUserExtSource().getLogin());
			}
		} catch (InternalErrorException e) {
		}
	}

	public void processExtSourceRemoved(String msg, MessageBeans beans) {
		// ensure we have the correct beans available
		if(beans.getUser() == null || beans.getUserExtSource() == null) {
			return;
		}
		try {
			if(beans.getUserExtSource().getExtSource().getType().equals(ExtSourcesManager.EXTSOURCE_IDP)) {
				perunUser.removePrincipal(beans.getUser(), beans.getUserExtSource().getLogin());
			}
		} catch (InternalErrorException e) {
		}
	}

}
