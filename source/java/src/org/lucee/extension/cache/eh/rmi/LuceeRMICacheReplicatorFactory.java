package org.lucee.extension.cache.eh.rmi;

import lucee.commons.io.log.Log;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.config.Config;
import net.sf.ehcache.distribution.RMISynchronousCacheReplicator;
import net.sf.ehcache.event.CacheEventListener;
import net.sf.ehcache.event.CacheEventListenerFactory;
import net.sf.ehcache.util.PropertyUtil;

import java.util.Properties;


/**
 * Creates an RMICacheReplicator using properties. Config lines look like:
 * <pre>&lt;cacheEventListenerFactory class="net.sf.ehcache.distribution.RMICacheReplicatorFactory"
 * properties="
 * replicateAsynchronously=true,
 * replicatePuts=true
 * replicateUpdates=true
 * replicateUpdatesViaCopy=true
 * replicateRemovals=true
 * "/&gt;</pre>
 *
 * @author <a href="mailto:gluck@thoughtworks.com">Greg Luck</a>
 * @version $Id: RMICacheReplicatorFactory.java 5594 2012-05-07 16:04:31Z cdennis $
 */
public class LuceeRMICacheReplicatorFactory extends CacheEventListenerFactory {

    /**
     * A default for the amount of time the replication thread sleeps after it detects the replicationQueue is empty
     * before checking again.
     */
    protected static final int DEFAULT_ASYNCHRONOUS_REPLICATION_INTERVAL_MILLIS = 1000;
    
    /**
     * A default for the maximum number of operations in an RMI message.
     */
    protected static final int DEFAULT_ASYNCHRONOUS_REPLICATION_MAXIMUM_BATCH_SIZE = 1000;

    private static final String REPLICATE_PUTS = "replicatePuts";
    private static final String REPLICATE_PUTS_VIA_COPY = "replicatePutsViaCopy";
    private static final String REPLICATE_UPDATES = "replicateUpdates";
    private static final String REPLICATE_UPDATES_VIA_COPY = "replicateUpdatesViaCopy";
    private static final String REPLICATE_REMOVALS = "replicateRemovals";
    private static final String REPLICATE_ASYNCHRONOUSLY = "replicateAsynchronously";
    private static final String ASYNCHRONOUS_REPLICATION_INTERVAL_MILLIS = "asynchronousReplicationIntervalMillis";
    private static final String ASYNCHRONOUS_REPLICATION_MAXIMUM_BATCH_SIZE = "asynchronousReplicationMaximumBatchSize";
    private static final int MINIMUM_REASONABLE_INTERVAL = 10;

	private Log log;
	private Config config;
    
    public LuceeRMICacheReplicatorFactory() {
    	try{
    		config = CFMLEngineFactory.getInstance().getThreadConfig();
    		this.log=config.getLog("application");
    	}
    	catch(Exception e) {
    		e.printStackTrace();
    	}
    }
    
    /**
     * Create a <code>CacheEventListener</code> which is also a CacheReplicator.
     * <p/>
     * The defaults if properties are not specified are:
     * <ul>
     * <li>replicatePuts=true
     * <li>replicatePutsViaCopy=true
     * <li>replicateUpdates=true
     * <li>replicateUpdatesViaCopy=true
     * <li>replicateRemovals=true;
     * <li>replicateAsynchronously=true
     * <li>asynchronousReplicationIntervalMillis=1000
     * </ul>
     *
     * @param properties implementation specific properties. These are configured as comma
     *                   separated name value pairs in ehcache.xml e.g.
     *                   <p/>
     *                   <code>
     *                   &lt;cacheEventListenerFactory class="net.sf.ehcache.distribution.RMICacheReplicatorFactory"
     *                   properties="
     *                   replicateAsynchronously=true,
     *                   replicatePuts=true
     *                   replicateUpdates=true
     *                   replicateUpdatesViaCopy=true
     *                   replicateRemovals=true
     *                   asynchronousReplicationIntervalMillis=1000
     *                   "/&gt;</code>
     * @return a constructed CacheEventListener
     */
    public final CacheEventListener createCacheEventListener(Properties properties) {
        boolean replicatePuts = extractReplicatePuts(properties);
        boolean replicatePutsViaCopy = extractReplicatePutsViaCopy(properties);
        boolean replicateUpdates = extractReplicateUpdates(properties);
        boolean replicateUpdatesViaCopy = extractReplicateUpdatesViaCopy(properties);
        boolean replicateRemovals = extractReplicateRemovals(properties);
        boolean replicateAsynchronously = extractReplicateAsynchronously(properties);
        int replicationIntervalMillis = extractReplicationIntervalMilis(properties);
        int maximumBatchSize = extractMaximumBatchSize(properties);

        if (replicateAsynchronously) {
            this.log.debug("ehcache", "Replicating asynchronously...");
            return new LuceeRMIAsynchronousCacheReplicator(config,log,
                    replicatePuts,
                    replicatePutsViaCopy,
                    replicateUpdates,
                    replicateUpdatesViaCopy,
                    replicateRemovals,
                    replicationIntervalMillis,
                    maximumBatchSize);
        } else {
            this.log.debug("ehcache", "Replicating synchronously...");
            return new RMISynchronousCacheReplicator(
                    replicatePuts,
                    replicatePutsViaCopy,
                    replicateUpdates,
                    replicateUpdatesViaCopy,
                    replicateRemovals);
        }
    }

    /**
     * Extracts the value of asynchronousReplicationIntervalMillis. Sets it to 1000ms if
     * either not set or there is a problem parsing the number
     * @param properties
     */
    protected int extractReplicationIntervalMilis(Properties properties) {
        int asynchronousReplicationIntervalMillis;
        String asynchronousReplicationIntervalMillisString =
                PropertyUtil.extractAndLogProperty(ASYNCHRONOUS_REPLICATION_INTERVAL_MILLIS, properties);
        if (asynchronousReplicationIntervalMillisString != null) {
            try {
                int asynchronousReplicationIntervalMillisCandidate =
                        Integer.parseInt(asynchronousReplicationIntervalMillisString);
                if (asynchronousReplicationIntervalMillisCandidate < MINIMUM_REASONABLE_INTERVAL) {
                    log.debug("ehcache","Trying to set the asynchronousReplicationIntervalMillis to an unreasonable number." +
                            " Using the default instead.");
                    asynchronousReplicationIntervalMillis = DEFAULT_ASYNCHRONOUS_REPLICATION_INTERVAL_MILLIS;
                } else {
                    asynchronousReplicationIntervalMillis = asynchronousReplicationIntervalMillisCandidate;
                }
            } catch (NumberFormatException e) {
                log.warn("ehcache","Number format exception trying to set asynchronousReplicationIntervalMillis. " +
                        "Using the default instead. String value was: '" + asynchronousReplicationIntervalMillisString + "'");
                asynchronousReplicationIntervalMillis = DEFAULT_ASYNCHRONOUS_REPLICATION_INTERVAL_MILLIS;
            }
        } else {
            asynchronousReplicationIntervalMillis = DEFAULT_ASYNCHRONOUS_REPLICATION_INTERVAL_MILLIS;
        }
        return asynchronousReplicationIntervalMillis;
    }

    /**
     * Extracts the value of maximumBatchSize. Sets it to 1024 if
     * either not set or there is a problem parsing the number
     * @param properties
     */
    protected int extractMaximumBatchSize(Properties properties) {
        String maximumBatchSizeString = 
                PropertyUtil.extractAndLogProperty(ASYNCHRONOUS_REPLICATION_MAXIMUM_BATCH_SIZE, properties);
        if (maximumBatchSizeString == null) {
            return DEFAULT_ASYNCHRONOUS_REPLICATION_MAXIMUM_BATCH_SIZE;
        } else {
            try {
                return Integer.parseInt(maximumBatchSizeString);
            } catch (NumberFormatException e) {
                log.warn("ehcache","Number format exception trying to set maximumBatchSize. " +
                        "Using the default instead. String value was: '" + maximumBatchSizeString + "'");
                return DEFAULT_ASYNCHRONOUS_REPLICATION_MAXIMUM_BATCH_SIZE;
            }
        }
    }
    
    /**
     * Extracts the value of replicateAsynchronously from the properties
     * @param properties
     */
    protected boolean extractReplicateAsynchronously(Properties properties) {
        boolean replicateAsynchronously;
        String replicateAsynchronouslyString = PropertyUtil.extractAndLogProperty(REPLICATE_ASYNCHRONOUSLY, properties);
        if (replicateAsynchronouslyString != null) {
            replicateAsynchronously = PropertyUtil.parseBoolean(replicateAsynchronouslyString);
        } else {
            replicateAsynchronously = true;
        }
        return replicateAsynchronously;
    }

    /**
     * Extracts the value of replicateRemovals from the properties
     * @param properties
     */
    protected boolean extractReplicateRemovals(Properties properties) {
        boolean replicateRemovals;
        String replicateRemovalsString = PropertyUtil.extractAndLogProperty(REPLICATE_REMOVALS, properties);
        if (replicateRemovalsString != null) {
            replicateRemovals = PropertyUtil.parseBoolean(replicateRemovalsString);
        } else {
            replicateRemovals = true;
        }
        return replicateRemovals;
    }

    /**
     * Extracts the value of replicateUpdatesViaCopy from the properties
     * @param properties
     */
    protected boolean extractReplicateUpdatesViaCopy(Properties properties) {
        boolean replicateUpdatesViaCopy;
        String replicateUpdatesViaCopyString = PropertyUtil.extractAndLogProperty(REPLICATE_UPDATES_VIA_COPY, properties);
        if (replicateUpdatesViaCopyString != null) {
            replicateUpdatesViaCopy = PropertyUtil.parseBoolean(replicateUpdatesViaCopyString);
        } else {
            replicateUpdatesViaCopy = true;
        }
        return replicateUpdatesViaCopy;
    }

    /**
     * Extracts the value of replicatePutsViaCopy from the properties
     * @param properties
     */
    protected boolean extractReplicatePutsViaCopy(Properties properties) {
        boolean replicatePutsViaCopy;
        String replicatePutsViaCopyString = PropertyUtil.extractAndLogProperty(REPLICATE_PUTS_VIA_COPY, properties);
        if (replicatePutsViaCopyString != null) {
            replicatePutsViaCopy = PropertyUtil.parseBoolean(replicatePutsViaCopyString);
        } else {
            replicatePutsViaCopy = true;
        }
        return replicatePutsViaCopy;
    }

    /**
     * Extracts the value of replicateUpdates from the properties
     * @param properties
     */
    protected boolean extractReplicateUpdates(Properties properties) {
        boolean replicateUpdates;
        String replicateUpdatesString = PropertyUtil.extractAndLogProperty(REPLICATE_UPDATES, properties);
        if (replicateUpdatesString != null) {
            replicateUpdates = PropertyUtil.parseBoolean(replicateUpdatesString);
        } else {
            replicateUpdates = true;
        }
        return replicateUpdates;
    }

    /**
     * Extracts the value of replicatePuts from the properties
     * @param properties
     */
    protected boolean extractReplicatePuts(Properties properties) {
        boolean replicatePuts;
        String replicatePutsString = PropertyUtil.extractAndLogProperty(REPLICATE_PUTS, properties);
        if (replicatePutsString != null) {
            replicatePuts = PropertyUtil.parseBoolean(replicatePutsString);
        } else {
            replicatePuts = true;
        }
        return replicatePuts;
    }
}