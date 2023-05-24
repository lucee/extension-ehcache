package org.lucee.extension.cache.eh.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.loader.util.Util;

public class SerializerUtil {

	private static Class<?> clazz;
	private static Method ser;
	private static Class<?> base64CoderClass;
	private static Method decode;

	public static Class<?> loadClass() throws IOException {
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		return engine.getClassUtil().loadClass("lucee.runtime.converter.JavaConverter");
	}

	public static Class<?> loadBase64CoderClass() throws IOException {
		CFMLEngine engine = CFMLEngineFactory.getInstance();
		return engine.getClassUtil().loadClass("lucee.runtime.coder.Base64Coder");
	}

	public static String serialize(Object o) throws Exception {
		if (clazz == null)
			clazz = loadClass();

		if (ser == null)
			ser = clazz.getMethod("serialize", new Class[] { Serializable.class });
		try {
			return (String) ser.invoke(null, new Object[] { o });
		} catch (InvocationTargetException ite) {
			Throwable t = ite.getTargetException();
			if (t instanceof Exception)
				throw (Exception) t;
			throw new RuntimeException(t);
		}
	}

	public static Object evaluate(String str) throws Exception {
		if (base64CoderClass == null)
			base64CoderClass = loadBase64CoderClass();
		if (decode == null)
			decode = base64CoderClass.getMethod("decode", new Class[] { String.class, boolean.class });
		// lucee.runtime.converter.JavaConverter.decode(java.lang.String, boolean)
		byte[] raw;
		try {
			raw = (byte[]) decode.invoke(null, new Object[] { str, true });
		} catch (InvocationTargetException ite) {
			Throwable t = ite.getTargetException();
			if (t instanceof Exception)
				throw (Exception) t;
			throw new RuntimeException(t);
		}

		ByteArrayInputStream bais = new ByteArrayInputStream(raw);
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStreamImpl(CFMLEngineFactory.getInstance().getClass().getClassLoader(), bais);
			return ois.readObject();
		} catch (ClassNotFoundException cnfe) {
			String className = cnfe.getMessage();
			if (!Util.isEmpty(className, true)) {
				Class<?> clazz = CFMLEngineFactory.getInstance().getClassUtil().loadClass(className.trim());
				bais = new ByteArrayInputStream(raw);
				ois = new ObjectInputStreamImpl(clazz.getClassLoader(), bais);
				return ois.readObject();
			}

			throw CFMLEngineFactory.getInstance().getExceptionUtil().toIOException(cnfe);
		} finally {
			Util.closeEL(ois);
		}

	}

}
