component extends = "org.lucee.cfml.test.LuceeTestCase" labels="ehcache"  skip=true {

	public any function test() {
		var configFile = getDirectoryFromPath(getCurrentTemplatePath()) & "LDEV4886/ehcache.xml"; 
		var config = createObject( "java", "net.sf.ehcache.config.ConfigurationFactory" )
			.parseConfiguration(createObject("java", "java.io.File" )
			.init( configFile  ));
		var cacheManager = createObject("java", "net.sf.ehcache.CacheManager" ).create( config );
		var configHelper = createObject("java", "net.sf.ehcache.config.ConfigurationHelper" ).init( cacheManager, config );
		if (cacheManager.cacheExists('test')) {
			cacheManager.removeCache('test');
		}
		var testCache = configHelper.createDefaultCache().clone();
		testCache.setName('test');
		cacheManager.addCache(testCache);
		return cacheManager.getCache('test');
	}

}