package cz.metacentrum.perun.ldapc.processor.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.ldapc.model.PerunUser;
import cz.metacentrum.perun.ldapc.processor.EventDispatcher.MessageBeans;

public class UserAttributeProcessor extends AbstractAttributeProcessor {

	private final static Logger log = LoggerFactory.getLogger(UserAttributeProcessor.class);

	@Autowired
	protected PerunUser perunUser;
	
	private Pattern userSetPattern = Pattern.compile(" set for User:\\[(.*)\\]");
	private Pattern userRemovePattern = Pattern.compile(" removed for User:\\[(.*)\\]");
	private Pattern userAllAttrsRemovedPattern = Pattern.compile("All attributes removed for User:\\[(.*)\\]");

	//UserExtSources patterns
	private Pattern addUserExtSourcePattern = Pattern.compile("UserExtSource:\\[(.*)\\] added to User:\\[(.*)\\]");
	private Pattern removeUserExtSourcePattern = Pattern.compile("UserExtSource:\\[(.*)\\] removed from User:\\[(.*)\\]");
	
	
	@Override
	public void processEvent(String msg, MessageBeans beans) {
		Matcher matcher = userSetPattern.matcher(msg);
		int mask = MessageBeans.ATTRIBUTE_F | MessageBeans.USER_F;
		if(matcher.find() && (beans.getPresentBeansMask() & mask) == mask) {
			processAttributeSet(msg, beans);
			return;
		}
		matcher = userRemovePattern.matcher(msg);
		mask = MessageBeans.ATTRIBUTEDEF_F | MessageBeans.USER_F;
		if(matcher.find() && (beans.getPresentBeansMask() & mask) == mask ) {
			processAttributeRemoved(msg, beans);
			return;
		}
		matcher = userAllAttrsRemovedPattern.matcher(msg);
		if(matcher.find() && (beans.getPresentBeansMask() & mask) == mask) {
			processAllAttributesRemoved(msg, beans);
			return;
		}
		// OK - we do not know how to handle this one
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
	}
}
