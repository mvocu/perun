package cz.metacentrum.perun.ldapc.processor.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import cz.metacentrum.perun.ldapc.processor.EventDispatcher.MessageBeans;

public class RegexpDispatchEventCondition extends SimpleDispatchEventCondition {

	private final static Logger log = LoggerFactory.getLogger(RegexpDispatchEventCondition.class);

	private Pattern pattern;

	@Required
	public void setPattern(String regexp) {
		this.pattern = Pattern.compile(regexp, Pattern.MULTILINE | Pattern.DOTALL);
	}

	@Override
	public boolean isApplicable(MessageBeans beans, String msg) {
		Matcher matcher = pattern.matcher(msg);
		boolean match = matcher.find();
		// log.debug("Matching {} against {} returned {}", this.pattern.toString(), msg, match);
		return super.isApplicable(beans, msg) && match;
	}
}
