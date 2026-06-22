package org.lucee.extension.cache.eh.util;

import java.lang.reflect.Method;

import lucee.commons.io.log.Log;
import lucee.runtime.config.Config;

/**
 * A class loader that delegates to the Lucee environment class loader, falling
 * back to the extension's own class loader. The OSGi bundle fallback is no
 * longer needed since Maven-based extensions load all JARs on a standard
 * classloader without bundle isolation.
 */
public class EHCacheClassLoader extends ClassLoader {
	private Config config;
	private ClassLoader coreEnvClassLoader;

	public EHCacheClassLoader(Config config) {
		this.config = config;

		try {
			Method m = config.getClass().getMethod("getClassLoaderEnv", new Class[0]);
			this.coreEnvClassLoader = (ClassLoader) m.invoke(config, new Object[0]);
		} catch (Exception e) {
			// do nothing
		}
	}

	private Log getLogger() {
		return this.config.getLog("application");
	}

	@Override
	public Class<?> loadClass(final String className) throws ClassNotFoundException {
		Log log = getLogger();

		log.debug("ehcache", "Loading class " + className);

		if (this.coreEnvClassLoader != null) {
			try {
				return this.coreEnvClassLoader.loadClass(className);
			} catch (ClassNotFoundException ex) {
				log.debug("ehcache", "Could not find " + className + " in EnvClassLoader, trying extension classloader.");
			}
		}

		try {
			return EHCacheClassLoader.class.getClassLoader().loadClass(className);
		} catch (ClassNotFoundException ex) {
			log.debug("ehcache", "Could not find " + className + " in any class loader!");
			throw ex;
		}
	}

	public ClassLoader getClassLoader() {
		return EHCacheClassLoader.class.getClassLoader();
	}

}
