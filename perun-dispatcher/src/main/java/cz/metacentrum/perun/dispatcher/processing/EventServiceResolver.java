package cz.metacentrum.perun.dispatcher.processing;

import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Service;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.PrivilegeException;
import cz.metacentrum.perun.core.api.exceptions.ServiceNotExistsException;
import cz.metacentrum.perun.dispatcher.exceptions.InvalidEventMessageException;

import java.util.Map;
import java.util.Set;

public interface EventServiceResolver {

	Map<Facility, Set<Service>> parseEvent(String event) throws InvalidEventMessageException, ServiceNotExistsException, InternalErrorException, PrivilegeException;

}
