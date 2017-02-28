package cz.metacentrum.perun.dispatcher.jms;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pool of JMS message queues for engines. Holds concurrent map of engine ID to instance of a queue.
 *
 * @see DispatcherQueue
 *
 * @author Michal Karm Babacek
 * @author Pavel Zlámal <zlamal@cesnet.cz>
 */
@org.springframework.stereotype.Service(value = "dispatcherQueuePool")
public class DispatcherQueuePool {

	private ConcurrentHashMap<Integer, DispatcherQueue> dispatcherQueuePool = new ConcurrentHashMap<Integer, DispatcherQueue>();
	private Iterator<DispatcherQueue> current = null;

	/**
	 * Return true if engine with specified ID has a queue in a pool.
	 *
	 * @param clientID ID of Engine
	 * @return TRUE if engine has queue in pool / FALSE otherwise
	 */
	public boolean isThereDispatcherQueueForClient(int clientID) {
		return dispatcherQueuePool.containsKey(clientID);
	}

	/**
	 * Get JMS queue for specified Engine ID.
	 *
	 * @param clientID ID of Engine
	 * @return Instance of a JMS queue
	 */
	public DispatcherQueue getDispatcherQueueByClient(int clientID) {
		if(clientID < 0) return null;
		return dispatcherQueuePool.get(clientID);
	}

	/**
	 * Add JMS queue to the pool
	 *
	 * @param dispatcherQueue Queue to be added
	 */
	public void addDispatcherQueue(DispatcherQueue dispatcherQueue) {
		dispatcherQueuePool.put(dispatcherQueue.getClientID(), dispatcherQueue);
		current = null;
	}

	/**
	 * Remove JMS queue from the pool
	 *
	 * @param dispatcherQueue Queue to be removed
	 */
	public void removeDispatcherQueue(DispatcherQueue dispatcherQueue) {
		current = null;
		dispatcherQueuePool.remove(dispatcherQueue.getClientID());
	}

	/**
	 * Return current size of the queues pool
	 *
	 * @return Size of the queues pool
	 */
	public int poolSize() {
		return dispatcherQueuePool.size();
	}

	/**
	 * Return all JMS queues in the pool as unmodifiable collection.
	 *
	 * @return All JMS queues from pool
	 */
	public Collection<DispatcherQueue> getPool() {
		return Collections.unmodifiableCollection(dispatcherQueuePool.values());
	}

	/**
	 * Return first available JMS queue or null.
	 * Used when Task is going to be assigned to its first queue.
	 *
	 * @return First available queue from the pool or null
	 */
	public DispatcherQueue getAvailableQueue() {
		if(dispatcherQueuePool.isEmpty()) {
			return null;
		}
		if(current == null || !current.hasNext()) {
			current = dispatcherQueuePool.values().iterator();
		}
		return current.next();
	}

	/**
	 * Remove JMS queue from the pool by ID of Engine.
	 *
	 * @param clientID ID of Engine
	 */
	public void removeDispatcherQueue(int clientID) {
		current = null;
		dispatcherQueuePool.remove(clientID);
	}

}
