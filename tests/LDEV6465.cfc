component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache" {

	variables.cacheName = "ldev6465";
	// a Jetty class from the Terracotta REST management server, shipped by the stock ehcache jar
	variables.jettyResource = "rest-management-private-classpath/org/eclipse/jetty/server/Server.class_terracotta";

	function beforeAll() {
		application action="update" caches={
			"#variables.cacheName#": {
				class: "org.lucee.extension.cache.eh.EHCache",
				storage: false,
				custom: { "distributed": "off", "maxelementsinmemory": "1000" },
				default: ""
			}
		};
	}

	function run( testResults, testBox ) {

		describe( "LDEV-6465 ehcache is bundled without the REST management server (Jetty)", function() {

			it( "cache put/get works", function() {
				cachePut( id: "ldev6465", value: "ok", cacheName: variables.cacheName );
				expect( cacheGet( id: "ldev6465", cacheName: variables.cacheName ) ).toBe( "ok" );
			});

			it( "net.sf.ehcache is loaded from the extension jar", function() {
				var cl = getCacheClassLoader();
				var extensionJar = jarOf( cl.getResource( "org/lucee/extension/cache/eh/EHCache.class" ) );
				var ehcacheJar = jarOf( cl.getResource( "net/sf/ehcache/CacheManager.class" ) );
				expect( ehcacheJar ).toBe( extensionJar );
			});

			it( "rest-management-private-classpath is not on the classpath", function() {
				var cl = getCacheClassLoader();
				expect( isNull( cl.getResource( variables.jettyResource ) ) ).toBeTrue();
			});

		});
	}

	private function getCacheClassLoader() {
		var cache = createObject( "java", "lucee.runtime.cache.CacheUtil" ).getCache( getPageContext(), variables.cacheName );
		expect( cache.getClass().getName() ).toBe( "org.lucee.extension.cache.eh.EHCache" );
		return cache.getClass().getClassLoader();
	}

	private string function jarOf( resourceUrl ) {
		expect( isNull( arguments.resourceUrl ) ).toBeFalse( "resource not found" );
		var u = arguments.resourceUrl.toExternalForm();
		expect( u ).toInclude( "!/" );
		return listFirst( u, "!" );
	}
}
