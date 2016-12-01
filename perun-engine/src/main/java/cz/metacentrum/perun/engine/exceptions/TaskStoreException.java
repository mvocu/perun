package cz.metacentrum.perun.engine.exceptions;


import cz.metacentrum.perun.core.api.exceptions.PerunException;

public class TaskStoreException extends PerunException {
	public TaskStoreException(String message) {
		super(message);
	}

	public TaskStoreException(String message, Throwable cause) {
		super(message, cause);
	}

	public TaskStoreException(Throwable cause) {
		super(cause);
	}
}
