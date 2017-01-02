/**
 *
 * Copyright (c) 2014, the Railo Company Ltd. All rights reserved.
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
 **/
package org.lucee.extension.cache.eh;

import java.util.List;

import org.lucee.extension.cache.CacheSupport;

import lucee.commons.io.cache.Cache;
import lucee.commons.io.cache.CacheEntry;
import lucee.commons.io.cache.CachePro;
import lucee.runtime.type.Struct;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;

public abstract class EHCacheSupport extends CacheSupport implements Cache {

	@Override
	public boolean contains(String key) {
		if(!getCache().isKeyInCache(key))return false;
		return getCache().get(key)!=null;
	}

	@Override
	public Struct getCustomInfo() {
		
		Struct info=super.getCustomInfo();
		// custom
		CacheConfiguration conf = getCache().getCacheConfiguration();
		info.setEL("disk_expiry_thread_interval", new Double(conf.getDiskExpiryThreadIntervalSeconds()));
		info.setEL("disk_spool_buffer_size", new Double(conf.getDiskSpoolBufferSizeMB()*1024*1024));
		info.setEL("max_elements_in_memory", new Double(conf.getMaxElementsInMemory()));
		info.setEL("max_elements_on_disk", new Double(conf.getMaxElementsOnDisk()));
		info.setEL("time_to_idle", new Double(conf.getTimeToIdleSeconds()));
		info.setEL("time_to_live", new Double(conf.getTimeToLiveSeconds()));
		info.setEL("name", conf.getName());
		return info;
	}

	@Override
	public List<String> keys() {
		return getCache().getKeysWithExpiryCheck();
	}
	
	@Override
	public void put(String key, Object value, Long idleTime, Long liveTime) {
		boolean hasTime = idleTime!=null || liveTime!=null;
		Integer idle = idleTime==null?null : new Integer( (int)(idleTime.longValue()/1000) );
		Integer live = liveTime==null?null : new Integer( (int)(liveTime.longValue()/1000) );
		
		if(hasTime)getCache().put(new Element(key, value ,false, idle, live));
		else getCache().put(new Element(key, value));
	}



	@Override
	public CachePro decouple() {
		// is already decoupled by default
		return this;
	}
	

	@Override
	public CacheEntry getQuiet(String key, CacheEntry defaultValue){
		try {
			return new EHCacheEntry(getCache().getQuiet(key));
		} catch (Throwable t) {
			return defaultValue;
		}
	}
	
	@Override
	public CacheEntry getQuiet(String key) {
		return new EHCacheEntry(getCache().getQuiet(key));
	}

	protected abstract net.sf.ehcache.Cache getCache();
	
	
}