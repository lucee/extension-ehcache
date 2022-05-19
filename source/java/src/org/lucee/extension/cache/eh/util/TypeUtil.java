package org.lucee.extension.cache.eh.util;

import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.io.Serializable;

import org.lucee.extension.cache.eh.EHCacheSupport;
import org.lucee.extension.cache.util.print;

import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Array;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.Struct;
import lucee.runtime.type.dt.DateTime;
import lucee.runtime.type.dt.TimeSpan;
import lucee.runtime.util.Creation;
import lucee.runtime.Component;

public class TypeUtil {
	
	private static Creation creator;

	/**
	 * converts the value to a type the JVM understands
	 * @param value
	 * @return
	 */
	public static Object toJVM(Object value) {
		if(value==null) return null;
		//print.e("jvm:"+value.getClass().getName());
		
		// DateTime
		if(value instanceof DateTime) {
			return new Date(((DateTime)value).getTime());
		}
		// TimeSpan
		if(value instanceof TimeSpan) {
			return ((TimeSpan)value).castToDoubleValue(0);
		}

		if(value instanceof Collection && !(value instanceof Component)) {
			// Array
			if(value instanceof Array) {
				List<Object> list=new LinkedList<Object>();
				Iterator<Object> it = ((Array)value).valueIterator();
				while(it.hasNext()) {
					list.add(toJVM(it.next()));
				}
				return list;
			}
			// Struct
			if(value instanceof Struct) {
				Iterator<Entry<Key, Object>> it = ((Struct)value).entryIterator();
				Map<String,Object> map=new ConcurrentHashMap<String,Object>();
				Entry<Key, Object> e;
				while(it.hasNext()) {
					e = it.next();
					map.put(e.getKey().getString(),toJVM(e.getValue()));
				}
				return map;
			}
			
			// Query
		}	
		
		// Node Lucee extract raw node
		// Component
		// UDF
		
		ClassLoader cl = value.getClass().getClassLoader();

		if(cl!=null && cl!=ClassLoader.getSystemClassLoader()) {
			try {
				return "lucee-serialized:"+SerializerUtil.serialize((Serializable) value);
			}
			catch (Exception e) {
				print.e("Could not serialize item \"" + value.toString() + "\" in toJVM()");
				print.e(e);
			}
		}
		
		
		return value;
	}
	
	public static Object toCFML(Object value) {
		if(value==null) return null;
		
		if(value instanceof LinkedList) {
			Iterator it = ((LinkedList)value).iterator();
			Array arr = creator().createArray();
			while(it.hasNext()) {
				arr.appendEL(toCFML(it.next()));
			}
			return arr;
		}
		if(value instanceof ConcurrentHashMap) {
			Iterator<Entry> it = ((ConcurrentHashMap)value).entrySet().iterator();
			Struct sct = creator().createStruct();
			while(it.hasNext()) {
				Entry e = it.next();
				sct.setEL(e.getKey().toString(), toCFML(e.getValue()));
			}
			return sct;
		}
		if(value instanceof String && ((String)value).startsWith("lucee-serialized:")) {
			try {
				return SerializerUtil.evaluate(((String)value).substring(17));
			}
			catch (Exception e) {
				print.e("Could not deserialize item \"" + value.toString() + "\" in toCFML()");
				print.e(e);
			}
		}
		return value;
	}

	private static Creation creator() {
		if(creator==null) 
			creator=CFMLEngineFactory.getInstance().getCreationUtil();
		return creator;
	}
	
}
