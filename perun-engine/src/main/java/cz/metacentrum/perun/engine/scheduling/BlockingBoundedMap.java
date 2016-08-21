package cz.metacentrum.perun.engine.scheduling;

import java.util.Collection;

//TODO: does this need to be synchronized?
public interface BlockingBoundedMap<K, V> {
	V blockingPut(K key, V value) throws InterruptedException;

	V remove(K key);

	Collection<V> values();
}
