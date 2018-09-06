package org.lucee.extension.cache.eh.util;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;

public class SerializerUtil {
	
	private static Class<?> clazz;
	private static Method deser;
	private static Method ser;

	public static Class<?> loadClass() throws IOException {
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		return engine.getClassUtil().loadClass("lucee.runtime.converter.JavaConverter");
	}
	
	public static String serialize(Object o) throws Exception  {
		if(clazz==null) clazz=loadClass();
		
		if(ser==null) ser=clazz.getMethod("serialize", new Class[]{Serializable.class});
		try {
			return (String)ser.invoke(null, new Object[]{o});
		}
		catch(InvocationTargetException ite) {
			Throwable t = ite.getTargetException();
			if(t instanceof Exception) throw (Exception)t;
			throw new RuntimeException(t);
		}
	}
	
	public static Object evaluate(String str) throws Exception {
		if(clazz==null) clazz=loadClass();
		if(deser==null) deser=clazz.getMethod("deserialize", new Class[]{String.class});
		
		try {
			return deser.invoke(null, new Object[]{str});
		}
		catch(InvocationTargetException ite) {
			Throwable t = ite.getTargetException();
			if(t instanceof Exception) throw (Exception)t;
			throw new RuntimeException(t);
		}
   }

}
