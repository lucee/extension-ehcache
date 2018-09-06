package org.lucee.extension.cache.eh.rmi;

import net.sf.ehcache.distribution.RMISynchronousCacheReplicator;

public class LuceeRMISynchronousCacheReplicator extends RMISynchronousCacheReplicator {

	public LuceeRMISynchronousCacheReplicator(boolean replicatePuts, boolean replicatePutsViaCopy,
			boolean replicateUpdates, boolean replicateUpdatesViaCopy, boolean replicateRemovals) {
		super(replicatePuts, replicatePutsViaCopy, replicateUpdates, replicateUpdatesViaCopy, replicateRemovals);
		
	}

}
