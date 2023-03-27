component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache,cache" {
 	
	function beforeAll() {
		variables.postgres = server.getDatasource("postgres");

		systemOutput("variables.postgres=#serializeJSON(variables.postgres)#",1,1);
		systemOutput("notHasPostgres=#notHasPostgres()#",1,1);

		if( structCount(postgres) ) {
			// define datasource
			application name="LDEV-4429" action="update"  datasource=postgres;
		}
	}

	public function testCachePutEHCache() {
		createEHCache();
		testCachePut();
	}

	private function testCachePut() localMode="modern"  {
		var jsonbColl = createObject("java", "org.postgresql.util.PGobject");
		jsonbColl.setValue('{"a": "aab"}');

		cachePut("def",jsonbColl,createTimespan(0,0,0,30),createTimespan(0,0,0,30),"testCache4429")
		var cachedval = cacheGet(id ="def", region="testCache4429")
		expect(isInstanceOf(cachedval, "java.lang.Object")).toBe("true");
		expect(cachedval.getValue()).toBe('{"a": "aab"}');
	}

	public boolean function notHasPostgres() {
		return structCount(server.getDatasource("postgres")) == 0;
	}

	private function createEHCache() {
		var cacheConn = {
			class: 'org.lucee.extension.cache.eh.EHCache'
		  , storage: false
		  , custom: {
			  "bootstrapAsynchronously":"true",
			  "automatic_hostName":"",
			  "bootstrapType":"on",
			  "maxelementsinmemory":"10000",
			  "manual_rmiUrls":"",
			  "distributed":"automatic",
			  "automatic_multicastGroupAddress":"230.0.0.1",
			  "memoryevictionpolicy":"LRU",
			  "timeToIdleSeconds":"86400",
			  "maximumChunkSizeBytes":"5000000",
			  "automatic_multicastGroupPort":"4446",
			  "listener_socketTimeoutMillis":"120000",
			  "timeToLiveSeconds":"86400",
			  "diskpersistent":"true",
			  "manual_addional":"",
			  "replicateRemovals":"true",
			  "automatic_addional":"",
			  "overflowtodisk":"true",
			  "replicateAsynchronously":"true",
			  "maxelementsondisk":"10000000",
			  "listener_remoteObjectPort":"",
			  "asynchronousReplicationIntervalMillis":"1000",
			  "listener_hostName":"",
			  "replicateUpdates":"true",
			  "manual_hostName":"",
			  "automatic_timeToLive":"unrestricted",
			  "listener_port":""
		  }
		  , default: ''
		};
		application name="LDEV-4429" action="update"  caches={"testCache4429":cacheConn};
	}
}