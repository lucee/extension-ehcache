package org.lucee.extension.cache.eh.rmi;

import lucee.commons.io.log.Log;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;

import java.lang.ref.SoftReference;
import java.rmi.UnmarshalException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lucee.extension.cache.eh.util.CacheUtil;
import org.lucee.extension.cache.util.print;

import net.sf.ehcache.distribution.*;
import net.sf.ehcache.distribution.RmiEventMessage.RmiEventType;

/**
 * Listens to {@link net.sf.ehcache.CacheManager} and {@link net.sf.ehcache.Cache} events and propagates those to
 * {@link CachePeer} peers of the Cache asynchronously.
 * <p/>
 * Updates are guaranteed to be replicated in the order in which they are received.
 * <p/>
 * While much faster in operation than {@link RMISynchronousCacheReplicator}, it does suffer from a number
 * of problems. Elements, which may be being spooled to DiskStore may stay around in memory because references
 * are being held to them from {@link EventMessage}s which are queued up. The replication thread runs once
 * per second, limiting the build up. However a lot of elements can be put into a cache in that time. We do not want
 * to get an {@link OutOfMemoryError} using distribution in circumstances when it would not happen if we were
 * just using the DiskStore.
 * <p/>
 * Accordingly, the Element values in {@link EventMessage}s are held by {@link java.lang.ref.SoftReference} in the queue,
 * so that they can be discarded if required by the GC to avoid an {@link OutOfMemoryError}. A log message
 * will be issued on each flush of the queue if there were any forced discards. One problem with GC collection
 * of SoftReferences is that the VM (JDK1.5 anyway) will do that rather than grow the heap size to the maximum.
 * The workaround is to either set minimum heap size to the maximum heap size to force heap allocation at start
 * up, or put up with a few lost messages while the heap grows.
 *
 * @author Greg Luck
 * @version $Id: RMIAsynchronousCacheReplicator.java 5594 2012-05-07 16:04:31Z cdennis $
 */
public class LuceeRMIAsynchronousCacheReplicator extends RMISynchronousCacheReplicator {

    /**
     * A thread which handles replication, so that replication can take place asynchronously and not hold up the cache
     */
    private final Thread replicationThread;

    /**
     * The amount of time the replication thread sleeps after it detects the replicationQueue is empty
     * before checking again.
     */
    private final int replicationInterval;

    /**
     * The maximum number of Element replication in single RMI message.
     */
    private final int maximumBatchSize;

    /**
     * A queue of updates.
     */
    private final Queue<Object> replicationQueue = new ConcurrentLinkedQueue<Object>();

	private Log log;

    /**
     * Constructor for internal and subclass use
     */
    public LuceeRMIAsynchronousCacheReplicator(Config config, Log log,
            boolean replicatePuts,
            boolean replicatePutsViaCopy,
            boolean replicateUpdates,
            boolean replicateUpdatesViaCopy,
            boolean replicateRemovals,
            int replicationInterval,
            int maximumBatchSize) {
        super(replicatePuts,
                replicatePutsViaCopy,
                replicateUpdates,
                replicateUpdatesViaCopy,
                replicateRemovals);
        this.log=log;
        this.replicationInterval = replicationInterval;
        this.maximumBatchSize = maximumBatchSize;
        status = Status.STATUS_ALIVE;
        
        // IMPORTANT for classloader in async
        try {
			Thread.currentThread().setContextClassLoader(CacheUtil.getClassLoaderEnv(config));
		}
    	catch (PageException e) {
			e.printStackTrace();
			log.error("ehcache", e);
		}
    	
        
        replicationThread = new ReplicationThread(config,log);
        replicationThread.start();
    }

    /**
     * RemoteDebugger method for the replicationQueue thread.
     * <p/>
     * Note that the replicationQueue thread locks the cache for the entire time it is writing elements to the disk.
     */
    private void replicationThreadMain(ClassLoader cl) {
        while (true) {
        	dump("replicate",cl);
            // Wait for elements in the replicationQueue
            while (alive() && replicationQueue != null && replicationQueue.isEmpty()) {
                try {
                    Thread.sleep(replicationInterval);
                } catch (InterruptedException e) {
                    log.debug("ehcache","Spool Thread interrupted.");
                    return;
                }
            }
            if (notAlive()) {
                return;
            }
            try {
                writeReplicationQueue();
            } catch (Throwable e) {
                log.error("ehcache","Exception on flushing of replication queue: " + e.getMessage() + ". Continuing...", e);
            }
        }
    }
    private void dump(String title, ClassLoader cl) {
		print.e("------ "+title+" -------");
		Iterator<Thread> it = Thread.getAllStackTraces().keySet().iterator();
		while(it.hasNext()) {
			Thread t = it.next();
			print.e("- "+t.getName()); 
			if(t.getContextClassLoader()!=null && t.getContextClassLoader().getClass().getName().equals("sun.misc.Launcher$AppClassLoader")) {
				print.e("-- "+t.getContextClassLoader().getClass().getName()); 
				t.setContextClassLoader(cl);
			}
			
		}
	}

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation queues the put notification for in-order replication to peers.
     *
     * @param cache   the cache emitting the notification
     * @param element the element which was just put into the cache.
     */
    public final void notifyElementPut(final Ehcache cache, final Element element) throws CacheException {
        if (notAlive()) {
            return;
        }

        if (!replicatePuts) {
            return;
        }

        if (replicatePutsViaCopy) {
            if (!element.isSerializable()) {
                if (log.getLogLevel()>=Log.LEVEL_WARN) {
                    log.warn("ehcache","Object with key " + element.getObjectKey() + " is not Serializable and cannot be replicated.");
                }
                return;
            }
            addToReplicationQueue(new RmiEventMessage(cache, RmiEventType.PUT, null, element));
        } else {
            if (!element.isKeySerializable()) {
                if (log.getLogLevel()>=Log.LEVEL_WARN) {
                    log.warn("ehcache","Object with key " + element.getObjectKey()
                            + " does not have a Serializable key and cannot be replicated via invalidate.");
                }
                return;
            }
            addToReplicationQueue(new RmiEventMessage(cache, RmiEventType.REMOVE, element.getKey(), null));
        }

    }

    /**
     * Called immediately after an element has been put into the cache and the element already
     * existed in the cache. This is thus an update.
     * <p/>
     * The {@link net.sf.ehcache.Cache#put(net.sf.ehcache.Element)} method
     * will block until this method returns.
     * <p/>
     * Implementers may wish to have access to the Element's fields, including value, so the element is provided.
     * Implementers should be careful not to modify the element. The effect of any modifications is undefined.
     *
     * @param cache   the cache emitting the notification
     * @param element the element which was just put into the cache.
     */
    public final void notifyElementUpdated(final Ehcache cache, final Element element) throws CacheException {
        if (notAlive()) {
            return;
        }
        if (!replicateUpdates) {
            return;
        }

        if (replicateUpdatesViaCopy) {
            if (!element.isSerializable()) {
                if (log.getLogLevel()>=Log.LEVEL_WARN) {
                    log.warn("ehcache","Object with key " + element.getObjectKey() + " is not Serializable and cannot be updated via copy.");
                }
                return;
            }
            addToReplicationQueue(new RmiEventMessage(cache, RmiEventType.PUT, null, element));
        } else {
            if (!element.isKeySerializable()) {
                if (log.getLogLevel()>=Log.LEVEL_WARN) {
                    log.warn("ehcache","Object with key " + element.getObjectKey()
                            + " does not have a Serializable key and cannot be replicated via invalidate.");
                }
                return;
            }
            addToReplicationQueue(new RmiEventMessage(cache, RmiEventType.REMOVE, element.getKey(), null));
        }
    }

    /**
     * Called immediately after an attempt to remove an element. The remove method will block until
     * this method returns.
     * <p/>
     * This notification is received regardless of whether the cache had an element matching
     * the removal key or not. If an element was removed, the element is passed to this method,
     * otherwise a synthetic element, with only the key set is passed in.
     * <p/>
     *
     * @param cache   the cache emitting the notification
     * @param element the element just deleted, or a synthetic element with just the key set if
     *                no element was removed.
     */
    public final void notifyElementRemoved(final Ehcache cache, final Element element) throws CacheException {
        if (notAlive()) {
            return;
        }

        if (!replicateRemovals) {
            return;
        }

        if (!element.isKeySerializable()) {
            if (log.getLogLevel()>=Log.LEVEL_WARN) {
                log.warn("ehcache","Key " + element.getObjectKey() + " is not Serializable and cannot be replicated.");
            }
            return;
        }
        addToReplicationQueue(new RmiEventMessage(cache, RmiEventType.REMOVE, element.getKey(), null));
    }


    /**
     * Called during {@link net.sf.ehcache.Ehcache#removeAll()} to indicate that the all
     * elements have been removed from the cache in a bulk operation. The usual
     * {@link #notifyElementRemoved(net.sf.ehcache.Ehcache,net.sf.ehcache.Element)}
     * is not called.
     * <p/>
     * This notification exists because clearing a cache is a special case. It is often
     * not practical to serially process notifications where potentially millions of elements
     * have been bulk deleted.
     *
     * @param cache the cache emitting the notification
     */
    public void notifyRemoveAll(final Ehcache cache) {
        if (notAlive()) {
            return;
        }

        if (!replicateRemovals) {
            return;
        }

        addToReplicationQueue(new RmiEventMessage(cache, RmiEventType.REMOVE_ALL, null, null));
    }


    /**
     * Adds a message to the queue.
     * <p/>
     * This method checks the state of the replication thread and warns
     * if it has stopped and then discards the message.
     *
     * @param cacheEventMessage
     */
    protected void addToReplicationQueue(RmiEventMessage eventMessage) {
        if (!replicationThread.isAlive()) {
            log.error("ehcache","CacheEventMessages cannot be added to the replication queue because the replication thread has died.");
        } else {
            switch (eventMessage.getType()) {
                case PUT:
                    replicationQueue.add(new SoftReference(eventMessage));
                    break;
                default:
                    replicationQueue.add(eventMessage);
                    break;
            }
        }
    }


    /**
     * Gets called once per {@link #replicationInterval}.
     * <p/>
     * Sends accumulated messages in bulk to each peer. i.e. if ther are 100 messages and 1 peer,
     * 1 RMI invocation results, not 100. Also, if a peer is unavailable this is discovered in only 1 try.
     * <p/>
     * Makes a copy of the queue so as not to hold up the enqueue operations.
     * <p/>
     * Any exceptions are caught so that the replication thread does not die, and because errors are expected,
     * due to peers becoming unavailable.
     * <p/>
     * This method issues warnings for problems that can be fixed with configuration changes.
     */
    private void writeReplicationQueue() {
        List<EventMessage> eventMessages = extractEventMessages(maximumBatchSize);

        if (!eventMessages.isEmpty()) {
            for (CachePeer cachePeer : listRemoteCachePeers(eventMessages.get(0).getEhcache())) {
                try {
                    cachePeer.send(eventMessages);
                } catch (UnmarshalException e) {
                    String message = e.getMessage();
                    if (message.contains("Read time out") || message.contains("Read timed out")) {
                        log.warn("ehcache","Unable to send message to remote peer due to socket read timeout. Consider increasing" +
                                " the socketTimeoutMillis setting in the cacheManagerPeerListenerFactory. " +
                                "Message was: " + message);
                    } else {
                        log.debug("ehcache","Unable to send message to remote peer.  Message was: " + message);
                    }
                } catch (Throwable t) {
                    log.error("ehcache","Unable to send message to remote peer.  Message was: " + t.getMessage(), t);
                }
            }
        }
    }
    
    /**
     * Package protected List of cache peers
     *
     * @param cache
     * @return a list of {@link CachePeer} peers for the given cache, excluding the local peer.
     */
    private List<CachePeer> listRemoteCachePeers(Ehcache cache) {
        CacheManagerPeerProvider provider = cache.getCacheManager().getCacheManagerPeerProvider("RMI");
        return provider.listRemoteCachePeers(cache);
    }
    

    private void flushReplicationQueue() {
        while (!replicationQueue.isEmpty()) {
            writeReplicationQueue();
        }
    }

    /**
     * Extracts CacheEventMessages and attempts to get a hard reference to the underlying EventMessage
     * <p/>
     * If an EventMessage has been invalidated due to SoftReference collection of the Element, it is not
     * propagated. This only affects puts and updates via copy.
     *
     * @param replicationQueueCopy
     * @return a list of EventMessages which were able to be resolved
     */
    private List<EventMessage> extractEventMessages(int limit) {
        List<EventMessage> list = new ArrayList(Math.min(replicationQueue.size(), limit));
        
        int droppedMessages = 0;
        
        while (list.size() < limit) {
            Object polled = replicationQueue.poll();
            if (polled == null) {
                break;
            } else if (polled instanceof EventMessage) {
                list.add((EventMessage) polled);
            } else {
                EventMessage message = ((SoftReference<EventMessage>) polled).get();
                if (message == null) {
                    droppedMessages++;
                } else {
                    list.add(message);
                }
            }
        }
        
        if (droppedMessages > 0) {
            log.warn("ehcache",droppedMessages + " messages were discarded on replicate due to reclamation of " +
                    "SoftReferences by the VM. Consider increasing the maximum heap size and/or setting the " +
                    "starting heap size to a higher value.");
        }
        return list;
    }

    /**
     * A background daemon thread that writes objects to the file.
     */
    private final class ReplicationThread extends Thread {
        private Config config;
		private Log log;

		public ReplicationThread(Config config, Log log) {
            super("Replication Thread");
            this.config=config;
            this.log=log;
            setDaemon(true);
            setPriority(Thread.NORM_PRIORITY);
        }

        /**
         * RemoteDebugger thread method.
         */
        public final void run() {
        	ClassLoader cl = null;
        	try {
        		cl = CacheUtil.getClassLoaderEnv(config);
				Thread.currentThread().setContextClassLoader(cl);
			}
        	catch (PageException e) {
				e.printStackTrace();
				log.error("ehcache", e);
			}
        	
            replicationThreadMain(cl);
        }

		
    }

    /**
     * Give the replicator a chance to flush the replication queue, then cleanup and free resources when no longer needed
     */
    public final void dispose() {
        status = Status.STATUS_SHUTDOWN;
        flushReplicationQueue();
    }


    /**
     * Creates a clone of this listener. This method will only be called by ehcache before a cache is initialized.
     * <p/>
     * This may not be possible for listeners after they have been initialized. Implementations should throw
     * CloneNotSupportedException if they do not support clone.
     *
     * @return a clone
     * @throws CloneNotSupportedException if the listener could not be cloned.
     */
    public Object clone() throws CloneNotSupportedException {
        //shutup checkstyle
        super.clone();
        return new RMIAsynchronousCacheReplicator(replicatePuts, replicatePutsViaCopy,
                replicateUpdates, replicateUpdatesViaCopy, replicateRemovals, replicationInterval, maximumBatchSize);
    }


}