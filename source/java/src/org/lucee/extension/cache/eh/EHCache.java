/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Assosication Switzerland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package org.lucee.extension.cache.eh;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import lucee.commons.io.cache.CacheEntry;
import lucee.commons.io.cache.exp.CacheException;
import lucee.commons.io.res.Resource;
import lucee.commons.io.res.filter.ResourceNameFilter;
import lucee.commons.lang.types.RefBoolean;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;
import lucee.runtime.config.Config;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Excepton;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.ConfigurationFactory;
import net.sf.ehcache.distribution.RMIBootstrapCacheLoaderFactory;
import net.sf.ehcache.distribution.RMICacheManagerPeerListenerFactory;
import net.sf.ehcache.distribution.RMICacheManagerPeerProviderFactory;
import net.sf.ehcache.distribution.RMICacheReplicatorFactory;

import org.lucee.extension.cache.eh.rmi.LuceeRMICacheReplicatorFactory;
import org.lucee.extension.cache.eh.util.CacheUtil;
import org.lucee.extension.cache.eh.util.TypeUtil;

import lucee.commons.io.log.Log;

public class EHCache extends EHCacheSupport {
	
	
	static {
		System.setProperty("net.sf.ehcache.enableShutdownHook", "true");
	}
	
	private static final boolean DISK_PERSISTENT = true;
	private static final boolean ETERNAL = false;
	private static final int MAX_ELEMENTS_IN_MEMEORY = 10000;
	private static final int MAX_ELEMENTS_ON_DISK = 10000000;
	private static final String MEMORY_EVICTION_POLICY = "LRU";
	private static final boolean OVERFLOW_TO_DISK = true;
	private static final long TIME_TO_IDLE_SECONDS = 86400; 
	private static final long TIME_TO_LIVE_SECONDS = 86400;
	
	private static final boolean REPLICATE_PUTS = true;
	private static final boolean REPLICATE_PUTS_VIA_COPY = true;
	private static final boolean REPLICATE_UPDATES = true;
	private static final boolean REPLICATE_UPDATES_VIA_COPY = true;
	private static final boolean REPLICATE_REMOVALS = true;
	private static final boolean REPLICATE_ASYNC = true;
	private static final int ASYNC_REP_INTERVAL = 1000; 
	//private static Map managers=new HashMap();
	private static Map<String,Map<String,CacheManagerAndHash>> managersColl=new HashMap<String,Map<String,CacheManagerAndHash>>();
	
	//private net.sf.ehcache.Cache cache;
	private int hits;
	private int misses;
	private String cacheName;
	private ClassLoader classLoader;
	private CacheManagerAndHash mah;
		
	public static void flushAllCaches() {
		String[] names;
		Iterator<Map<String, CacheManagerAndHash>> _it = managersColl.values().iterator();
		Map<String, CacheManagerAndHash> _managers;
		while(_it.hasNext()) {
			_managers = _it.next();
			Iterator<Entry<String, CacheManagerAndHash>> it = _managers.entrySet().iterator();
			Entry<String, CacheManagerAndHash> entry;
			while(it.hasNext()){
				entry = it.next();
				CacheManagerAndHash cmah = entry.getValue();
				CacheManager manager=cmah.getInstance(false);
				boolean alreadyLoaded=true;
				if(manager==null){
					// not loaded yet
					alreadyLoaded=false;
					manager=cmah.getInstance(true);
				}
				try {
					names = manager.getCacheNames();
					for(int i=0;i<names.length;i++){
						manager.getCache(names[i]).flush();
					}
				}
				finally {
					if(!alreadyLoaded)cmah.shutdown();
				}
			}
		}
	}
	
	private static void clean(Resource dir) {
		Resource[] dirs = dir.listResources();
		Resource[] children;
		
		for(int i=0;i<dirs.length;i++){
			if(dirs[i].isDirectory()){
				//print.out(dirs[i]+":"+pathes.contains(dirs[i].getAbsolutePath()));
				children=dirs[i].listResources();
				if(children!=null && children.length>1)continue;
				clean(children);
				dirs[i].delete();
			}
		}
	}

	private static void clean(Resource[] arr) {
		if(arr!=null)for(int i=0;i<arr.length;i++){
			if(arr[i].isDirectory()){
				clean(arr[i].listResources());
			}
			arr[i].delete();
		}
	}

	private static void moveData(Resource dir, String hash, String[] cacheNames, Struct[] arguments) {
		String h;
		Resource trg = dir.getRealResource(hash);
		deleteData(dir, cacheNames);
		for(int i=0;i<cacheNames.length;i++){
			h=createHash(arguments[i]);
			if(h.equals(hash)){
				moveData(dir,cacheNames[i],trg);
			}
		}
		
	}

	private static void moveData(Resource dir, String cacheName, Resource trg) {
		cacheName=improveCacheName(cacheName);
		Resource[] dirs = dir.listResources();
		Resource index,data;
		// move 
		for(int i=0;i<dirs.length;i++){
			if(!dirs[i].equals(trg) && 
				dirs[i].isDirectory() && 
				(data=dirs[i].getRealResource(cacheName+".data")).exists() && 
				(index=dirs[i].getRealResource(cacheName+".index")).exists() ){
				
				try {
					index.moveTo(trg.getRealResource(cacheName+".index"));
					data.moveTo(trg.getRealResource(cacheName+".data"));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private static String improveCacheName(String cacheName) {
		if(cacheName.equalsIgnoreCase("default"))
			return "___default___";
		return cacheName;
	}
	private static String label(String cacheName) {
		if(cacheName.equalsIgnoreCase("___default___"))
			return "default";
		return cacheName;
	}

	private static void deleteData(Resource dir, String[] cacheNames) {
		Set<String> names=new HashSet<String>();
		for(int i=0;i<cacheNames.length;i++){
			names.add(improveCacheName(cacheNames[i]));
		}
		
		Resource[] dirs = dir.listResources();
		String name;
		// move 
		for(int i=0;i<dirs.length;i++){
			if(dirs[i].isDirectory()){
				Resource[] datas = dirs[i].listResources(new DataFiter());
				if(datas!=null) for(int y=0;y<datas.length;y++){
					name=datas[y].getName();
					name=name.substring(0,name.length()-5);
					if(!names.contains(name)){
						datas[y].delete();
						dirs[i].getRealResource(name+".index").delete();
					}
						
				}
			}
		}
	}

	private static void writeEHCacheXML(Resource hashDir, String xml) throws IOException {
		Charset charset;
		try {
			charset = CFMLEngineFactory.getInstance().getCastUtil().toCharset("UTF-8");
		}
		catch (Exception e) {
			charset=null;
		}
		ByteArrayInputStream is = new ByteArrayInputStream(charset==null?xml.getBytes():xml.getBytes(charset));
		OutputStream os = hashDir.getRealResource("ehcache.xml").getOutputStream();
		Util.copy(is, os,false,true);
	}

	private static String createHash(Struct args) {
		String dist = args.get("distributed","").toString().trim().toLowerCase();
		try {
			if(dist.equals("off")){
				return CFMLEngineFactory.getInstance().getSystemUtil().hashMd5(dist);
			}
			else if(dist.equals("automatic")){
				return CFMLEngineFactory.getInstance().getSystemUtil().hashMd5(
					dist+
					args.get("automatic_timeToLive","").toString().trim().toLowerCase()+
					args.get("automatic_addional","").toString().trim().toLowerCase()+
					args.get("automatic_multicastGroupPort","").toString().trim().toLowerCase()+
					args.get("automatic_multicastGroupAddress","").toString().trim().toLowerCase()+
					args.get("automatic_hostName","").toString().trim().toLowerCase()
				);
			}
			else {
				 return CFMLEngineFactory.getInstance().getSystemUtil().hashMd5(
					dist+
					args.get("manual_rmiUrls","").toString().trim().toLowerCase()+
					args.get("manual_addional","").toString().trim().toLowerCase()+
					args.get("listener_hostName","").toString().trim().toLowerCase()+
					args.get("listener_port","").toString().trim().toLowerCase()+
					args.get("listener_remoteObjectPort","").toString().trim().toLowerCase()+
					args.get("listener_socketTimeoutMillis","120000").toString().trim().toLowerCase()
				); 
			}
			
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}
	}
	
	private static String[] createHash(Struct[] arguments) {
		String[] hashes=new String[arguments.length];
		for(int i=0;i<arguments.length;i++){
			hashes[i]=createHash(arguments[i]);
		}
		return hashes;
	}

	private static String createXML(String path, String cacheName,Struct arguments, String hash, RefBoolean isDistributed) {
		getLogger().debug("ehcache", "Building ehCache XML...");

		isDistributed.setValue(false);
		StringBuilder xml=new StringBuilder();
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xml.append("<ehcache xsi:noNamespaceSchemaLocation=\"ehcache.xsd\">\n");
				
		// disk storage
		xml.append("<diskStore path=\"");
		xml.append(path);
		xml.append("\"/>\n");
		
		
		// RMI
		// Automatic
		if(arguments!=null && arguments.get("distributed","").equals("automatic")){
			// provider
			isDistributed.setValue(true);
			xml.append("<cacheManagerPeerProviderFactory \n");
			xml.append(" class=\""+RMICacheManagerPeerProviderFactory.class.getName()+"\"\n ");
			String add = arguments.get("automatic_addional","").toString().trim();
			String hostName=arguments.get("automatic_hostName","").toString().trim().toLowerCase();
			if(!Util.isEmpty(hostName)) add+=",hostName="+hostName;
			if(!Util.isEmpty(add) && !add.startsWith(","))add=","+add;
			add=add.replace('\n', ' ');
			xml.append(" properties=\"peerDiscovery=automatic" +
					", multicastGroupAddress="+arguments.get("automatic_multicastGroupAddress","").toString().trim().toLowerCase()+
					", multicastGroupPort="+arguments.get("automatic_multicastGroupPort","").toString().trim().toLowerCase()+
					", timeToLive="+toTimeToLive(arguments.get("automatic_timeToLive","").toString().trim().toLowerCase())+
					add+" \" />\n");
			
			//hostName=fully_qualified_hostname_or_ip,
		}
		// Manual
		else if(arguments!=null && arguments.get("distributed","").equals("manual")){
			// provider
			isDistributed.setValue(true);
			xml.append("<cacheManagerPeerProviderFactory");
			xml.append(" class=\""+RMICacheManagerPeerProviderFactory.class.getName()+"\" ");
			String add = arguments.get("manual_addional","").toString().trim();
			if(!Util.isEmpty(add) && !add.startsWith(","))add=","+add;
			add=add.replace('\n', ' ');
			xml.append(" properties=\"peerDiscovery=manual, rmiUrls="+arguments.get("manual_rmiUrls","").toString().trim().replace('\n', ' ')+
					add+"\"/>\n"); //propertySeparator=\",\" 
		}

		
		// Whenever RMI is being used, we must add the listener properties so we can bind
		// to the specified ports in the administration.
		//
		// This is important for "automatic" as well, so that we can configure specific
		// ports to use instead of having ehCache bind to an ephemeral port.
		if( isDistributed.toBooleanValue() ){
			StringBuilder sb=new StringBuilder();
			
			String hostName=arguments.get("listener_hostName","").toString().trim().toLowerCase();
			if(!Util.isEmpty(hostName)) add(sb,"hostName="+hostName);
			String port = arguments.get("listener_port","").toString().trim().toLowerCase();
			if(!Util.isEmpty(port)) add(sb,"port="+port);
			String remoteObjectPort = arguments.get("listener_remoteObjectPort","").toString().trim().toLowerCase();
			if(!Util.isEmpty(remoteObjectPort)) add(sb,"remoteObjectPort="+remoteObjectPort);
			String socketTimeoutMillis = arguments.get("listener_socketTimeoutMillis","").toString().trim().toLowerCase();
			if(!Util.isEmpty(socketTimeoutMillis) && !"120000".equals(socketTimeoutMillis)) 
				add(sb,"socketTimeoutMillis="+socketTimeoutMillis);

			getLogger().debug("ehcache", "Remote port = " + remoteObjectPort);
				
			xml.append("<cacheManagerPeerListenerFactory"); 
			xml.append(" class=\""+RMICacheManagerPeerListenerFactory.class.getName()+"\""); 
			if(sb.length()>0)xml.append(" properties=\""+sb+"\""); 
			xml.append("/>\n");
		// for non-distributed caches, we write an empty event listener
		} else {
			xml.append("<cacheManagerEventListenerFactory class=\"\" properties=\"\"/>\n");
		}

		xml.append("<defaultCache \n");
		xml.append("   diskPersistent=\"true\"\n");
		xml.append("   eternal=\"false\"\n");
		xml.append("   maxElementsInMemory=\"10000\"\n");
		xml.append("   maxElementsOnDisk=\"10000000\"\n");
		xml.append("   memoryStoreEvictionPolicy=\"LRU\"\n");
		xml.append("   timeToIdleSeconds=\"86400\"\n");
		xml.append("   timeToLiveSeconds=\"86400\"\n");
		xml.append("   overflowToDisk=\"true\"\n");
		xml.append("   diskSpoolBufferSizeMB=\"30\"\n");
		xml.append("   diskExpiryThreadIntervalSeconds=\"3600\"\n");
		xml.append(" />\n");
		
		// cache
		createCacheXml(xml,cacheName,arguments,isDistributed.toBooleanValue());
		
		xml.append("</ehcache>\n");

		String xmlContents = xml.toString();

		getLogger().debug("ehcache", "Finished building ehCache XML...");
		getLogger().debug("ehcache", "ehcache.xml = \n" + xmlContents);

		return xmlContents;
	}
	
	
	
	private static void add(StringBuilder sb,String str) {
		if(sb.length()>0)sb.append(", ");
		sb.append(str);
	}

	private static int toTimeToLive(String str) {
		if(str.indexOf("host")!=-1) return 0;
		if(str.indexOf("site")!=-1) return 1;
		if(str.indexOf("region")!=-1) return 64;
		if(str.indexOf("continent")!=-1) return 128;
		return 255;	
	}

	private static void createCacheXml(StringBuilder xml, String cacheName, Struct arguments, boolean isDistributed) {
		cacheName=improveCacheName(cacheName);
		// disk Persistent
		boolean diskPersistent=toBooleanValue(arguments.get("diskpersistent",Boolean.FALSE),DISK_PERSISTENT);
		
		// eternal
		boolean eternal=toBooleanValue(arguments.get("eternal",Boolean.FALSE),ETERNAL);
		
		// max elements in memory
		int maxElementsInMemory=toIntValue(arguments.get("maxelementsinmemory",new Integer(MAX_ELEMENTS_IN_MEMEORY)),MAX_ELEMENTS_IN_MEMEORY);
		
		// max elements on disk
		int maxElementsOnDisk=toIntValue(arguments.get("maxelementsondisk",new Integer(MAX_ELEMENTS_ON_DISK)),MAX_ELEMENTS_ON_DISK);
		
		// memory eviction policy
		String strPolicy=toString(arguments.get("memoryevictionpolicy",MEMORY_EVICTION_POLICY),MEMORY_EVICTION_POLICY);
		String policy = "LRU";
		if("FIFO".equalsIgnoreCase(strPolicy)) policy="FIFO";
		else if("LFU".equalsIgnoreCase(strPolicy)) policy="LFU";
		
		// overflow to disk
		boolean overflowToDisk=toBooleanValue(arguments.get("overflowtodisk",Boolean.FALSE),OVERFLOW_TO_DISK);
		
		// time to idle seconds
		long timeToIdleSeconds=toLongValue(arguments.get("timeToIdleSeconds",new Long(TIME_TO_IDLE_SECONDS)),TIME_TO_IDLE_SECONDS);
		
		// time to live seconds
		long timeToLiveSeconds=toLongValue(arguments.get("timeToLiveSeconds",new Long(TIME_TO_LIVE_SECONDS)),TIME_TO_LIVE_SECONDS);
		
	// REPLICATION
		boolean replicatePuts=toBooleanValue(arguments.get("replicatePuts",Boolean.FALSE),REPLICATE_PUTS);
		boolean replicatePutsViaCopy=toBooleanValue(arguments.get("replicatePutsViaCopy",Boolean.FALSE),REPLICATE_PUTS_VIA_COPY);
		boolean replicateUpdates=toBooleanValue(arguments.get("replicateUpdates",Boolean.FALSE),REPLICATE_UPDATES);
		boolean replicateUpdatesViaCopy=toBooleanValue(arguments.get("replicateUpdatesViaCopy",Boolean.FALSE),REPLICATE_UPDATES_VIA_COPY);
		boolean replicateRemovals=toBooleanValue(arguments.get("replicateRemovals",Boolean.FALSE),REPLICATE_REMOVALS);
		boolean replicateAsynchronously=toBooleanValue(arguments.get("replicateAsynchronously",Boolean.FALSE),REPLICATE_ASYNC);
		int asynchronousReplicationInterval=toIntValue(arguments.get("asynchronousReplicationIntervalMillis",new Integer(ASYNC_REP_INTERVAL)),ASYNC_REP_INTERVAL);
		
		
		
		xml.append("<cache name=\""+cacheName+"\"\n");
		xml.append("   diskPersistent=\""+diskPersistent+"\"\n");
		xml.append("   eternal=\""+eternal+"\"\n");
		xml.append("   maxElementsInMemory=\""+maxElementsInMemory+"\"\n");
		xml.append("   maxElementsOnDisk=\""+maxElementsOnDisk+"\"\n");
		xml.append("   memoryStoreEvictionPolicy=\""+policy+"\"\n");
		xml.append("   timeToIdleSeconds=\""+timeToIdleSeconds+"\"\n");
		xml.append("   timeToLiveSeconds=\""+timeToLiveSeconds+"\"\n");
		xml.append("   overflowToDisk=\""+overflowToDisk+"\"");
		xml.append(">\n");
		if(isDistributed){
			xml.append(" <cacheEventListenerFactory \n");
			xml.append(" class=\""+RMICacheReplicatorFactory.class.getName()+"\" \n");
			//xml.append(" class=\""+LuceeRMICacheReplicatorFactory.class.getName()+"\" \n");
			
			xml.append(" properties=\"replicateAsynchronously="+replicateAsynchronously+
					", asynchronousReplicationIntervalMillis="+asynchronousReplicationInterval+
					", replicatePuts="+replicatePuts+
					", replicatePutsViaCopy="+replicatePutsViaCopy+
					", replicateUpdates="+replicateUpdates+
					", replicateUpdatesViaCopy="+replicateUpdatesViaCopy+
					", replicateRemovals="+replicateRemovals+" \"");
			xml.append("/>\n");
			

			// BootStrap
			if(toBooleanValue(arguments.get("bootstrapType","false"),false)){
				xml.append("<bootstrapCacheLoaderFactory \n");
				xml.append("	class=\""+RMIBootstrapCacheLoaderFactory.class.getName()+"\" \n");
				xml.append("	properties=\"bootstrapAsynchronously="+toBooleanValue(arguments.get("bootstrapAsynchronously","true"),true)+
						", maximumChunkSizeBytes="+toLongValue(arguments.get("maximumChunkSizeBytes","5000000"),5000000L)+"\" \n");
				xml.append("	propertySeparator=\",\" /> \n");
			}
	        
			
		}
		xml.append(" </cache>\n");
	
		
	}
	public static void init(Config config,String[] cacheNames,Struct[] args) {
		// not used
	}

	public void init(String cacheName, Struct arguments) throws IOException {
		init(CFMLEngineFactory.getInstance().getThreadConfig(),cacheName, arguments);
	}
	
	@Override
	public void init(Config config,String cacheName,Struct arguments) throws IOException {
		
		this.cacheName=cacheName=improveCacheName(cacheName);
		
		// env stuff
		System.setProperty("net.sf.ehcache.enableShutdownHook", "true");
		try {
			getLogger().debug("ehcache", "Setting class loader context...");
			this.classLoader=CacheUtil.getClassLoaderEnv(config);
			setClassLoader();
			
		} catch (PageException pe) {
			getLogger().error("ehcache", "Failed to set class loader context...");
			throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(pe);
		}
		
		
		String hashArgs=createHash(arguments);
		
		// get manangers for this context
		Resource dir = config.getConfigDir().getRealResource("ehcache");
		if(!dir.isDirectory()) dir.createDirectory(true);
		Map<String, CacheManagerAndHash> managers = managersColl.get(dir.getAbsolutePath());
		if(managers==null) {
			managers=new HashMap<String, CacheManagerAndHash>();
			managersColl.put(dir.getAbsolutePath(), managers);
		}
		
		// get manager for that specific configuration (arguments)
		mah=managers.get(hashArgs);

		getLogger().debug("ehcache", "mah = " + ((mah == null) ? "null" : mah.toString()));

		if(mah==null) {
			Resource hashDir=dir.getRealResource(hashArgs);
			if(!hashDir.isDirectory())hashDir.createDirectory(true);
			RefBoolean isDistributed = CFMLEngineFactory.getInstance().getCreationUtil().createRefBoolean(false);
			String xml=createXML(hashDir.getAbsolutePath(), cacheName,arguments,hashArgs,isDistributed);
			mah=new CacheManagerAndHash(xml);// "ehcache_"+config.getIdentification().getId()
			managers.put(hashArgs, mah);
			this.isDistributed=isDistributed.toBooleanValue();
			if( this.isDistributed ){
				// we should serialize the results if we are putting copies of the elements in the cache
				this.isSerialized=(toBooleanValue(arguments.get("replicatePutsViaCopy",Boolean.FALSE),REPLICATE_PUTS_VIA_COPY) || toBooleanValue(arguments.get("replicateUpdatesViaCopy",Boolean.FALSE),REPLICATE_UPDATES_VIA_COPY)) ? true : false;
			}
			getLogger().debug("ehcache", "Writing EHCache XML!");
			// write the xml
			writeEHCacheXML(hashDir,xml);
		}
		
	}

	public void release() {
		if(mah==null) return;
		int remaining=mah.removeCache(cacheName);
		if(remaining<1)mah.shutdown();
		
	}

	protected void finalize() {
		release();
	}
	
	private void setClassLoader() {
		if(classLoader!=null && classLoader!=Thread.currentThread().getContextClassLoader())
			Thread.currentThread().setContextClassLoader(classLoader);
	}

	protected net.sf.ehcache.Cache getCache() {
		setClassLoader();
		
		// we do not create the cache before it is requested
		CacheManager man = mah.getInstance(true);
		Cache c = man.getCache(cacheName);
		if(c==null) {
			man.addCache(cacheName);
			c=man.getCache(cacheName);
		}
		
		if(c==null){
			CacheManager cm = mah.getInstance(false);
			CFMLEngine engine = CFMLEngineFactory.getInstance();
			Excepton exp = engine.getExceptionUtil();
			throw exp.createPageRuntimeException(
					exp.createApplicationException("there is no cache with name ["+label(cacheName)+"], available caches are ["+
			CFMLEngineFactory.getInstance().getListUtil().toList(cm==null?new String[]{}:cm.getCacheNames(), ", ")+"]"));
		}
		return c;
	}

	@Override
	public boolean remove(String key) {
		try	{
			return getCache().remove(key);
		}
		catch(Throwable t){
			if(t instanceof ThreadDeath) throw (ThreadDeath)t;
			return false;
		}
	}

	public CacheEntry getCacheEntry(String key) throws CacheException {
		try {
			misses++;
			Element el = getCache().get(key);
			if(el==null)throw new CacheException("there is no entry in cache with key ["+key+"]");
			hits++;
			misses--;
			return new EHCacheEntry(this,el);
		}
		catch(IllegalStateException ise) {
			throw new CacheException(ise.getMessage());
		}
		catch(net.sf.ehcache.CacheException ce) {
			throw new CacheException(ce.getMessage());
		}
	}

	public CacheEntry getCacheEntry(String key, CacheEntry defaultValue) {
		try {
			Element el = getCache().get(key);
			if(el!=null){
				hits++;
				return new EHCacheEntry(this,el);
			}
		}
		catch(Throwable t) {
			if(t instanceof ThreadDeath) throw (ThreadDeath)t;
			misses++;
		}
		return defaultValue;
	}

	@Override
	public Object getValue(String key) throws CacheException {
		try {
			misses++;
			Element el = getCache().get(key);
			if(el==null)throw new CacheException("there is no entry in cache with key ["+key+"]");
			misses--;
			hits++;
			return isDistributed?TypeUtil.toCFML(el.getObjectValue()):el.getObjectValue();
		}
		catch(IllegalStateException ise) {
			throw new CacheException(ise.getMessage());
		}
		catch(net.sf.ehcache.CacheException ce) {
			throw new CacheException(ce.getMessage());
		}
	}

	@Override
	public Object getValue(String key, Object defaultValue) {
		try {
			Element el = getCache().get(key);
			if(el!=null){
				hits++;
				return isDistributed?TypeUtil.toCFML(el.getObjectValue()):el.getObjectValue();
			}
		}
		catch(Exception e) {
			misses++;
		}
		return defaultValue;
	}

	@Override
	public long hitCount() {
		return hits;
	}

	@Override
	public long missCount() {
		return misses;
	}

	public void remove() {
		setClassLoader();
		CacheManager singletonManager = CacheManager.getInstance();
		if(singletonManager.cacheExists(cacheName))
			singletonManager.removeCache(cacheName);
		
	}
	
	@Override
	public int clear() throws IOException {
		int size=getCache().getSize();
		getCache().removeAll();
		return size;
	}
	
	

	private static boolean toBooleanValue(Object o, boolean defaultValue) {
		if(o instanceof Boolean) return ((Boolean)o).booleanValue();
        else if(o instanceof Number) return (((Number)o).doubleValue())!=0;
        else if(o instanceof String) {
        	String str = o.toString().trim().toLowerCase();
            if(str.equals("yes") || str.equals("on") || str.equals("true")) return true;
            else if(str.equals("no") || str.equals("false") || str.equals("off")) return false;
        }
        return defaultValue;
	}

	private static String toString(Object o, String defaultValue) {
		if(o instanceof String)return o.toString();
		return defaultValue;
	}

	private static int toIntValue(Object o, int defaultValue) {
		if(o instanceof Number) return ((Number)o).intValue();
		try{
		return Integer.parseInt(o.toString());
		}
		catch(Throwable t){
			if(t instanceof ThreadDeath) throw (ThreadDeath)t;
			return defaultValue;
		}
	}

	private static long toLongValue(Object o, long defaultValue) {
		if(o instanceof Number) return ((Number)o).longValue();
		try{
			return Long.parseLong(o.toString());
		}
		catch(Throwable t){
			if(t instanceof ThreadDeath) throw (ThreadDeath)t;
			return defaultValue;
		}
	}
	

}
	class CacheManagerAndHash {

		//Configuration conf;
		final String hash;
		private CacheManager _manager;
		private String xml;
		final String name;

		/*public CacheManagerAndHash(Configuration conf, String hash, int x) {
			this.conf=conf;
			this.hash=hash;
		}*/
		
		public CacheManagerAndHash(String xml) throws IOException {
			this.xml=xml;
			this.hash=CFMLEngineFactory.getInstance().getSystemUtil().hashMd5(xml);
			this.name="ehcache_"+hash;
		}
		
		public int removeCache(String cacheName) {
			CacheManager man = getInstance(false);
			if(man==null) return -1;
			
			String[] names = man.getCacheNames();
			for(String name:names) {
				if(name.equals(cacheName)) {
					man.removeCache(cacheName);
					return names.length-1;
				}
			}
			return names.length;
		}

		private Configuration createConfig() {
			Charset charset;
			try {
				charset = CFMLEngineFactory.getInstance().getCastUtil().toCharset("UTF-8");
			} catch (Exception e) {
				charset=null;
			}
			byte[] barr = charset!=null?xml.getBytes(charset):xml.getBytes();
			Configuration conf = ConfigurationFactory.parseConfiguration(new ByteArrayInputStream(barr));
			conf.setName(name);
			return conf;
			
		}

		public CacheManager getInstance(boolean createIfNecessary) {
			if(_manager==null) {
				if(createIfNecessary)
					_manager=CacheManager.newInstance(createConfig());
			}
			else {
				// This should never happen, but anyway, we simply make sure
				if(_manager.getStatus().equals(Status.STATUS_SHUTDOWN)) {
					_manager=null;
					if(createIfNecessary)_manager=CacheManager.newInstance(createConfig());
					
				}
			}
			return _manager;
		}

		public void shutdown() {
			if(_manager!=null) {
				//print.ds("hhhhhhhhhhhhhhh shutdown hhhhhhhhhhhhhhhhh");
				CacheManager m = _manager;
				_manager=null;
				m.shutdown();
			}
		}
	}

	class DataFiter implements ResourceNameFilter {
	
		@Override
		public boolean accept(Resource parent, String name) {
			return name.endsWith(".data");
		}
	
	}