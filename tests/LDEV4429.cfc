component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache,cache" {

	public function testCachePutEHCache() {
		createEHCache();
		testCachePut();
	}

	private function testCachePut() localMode="modern" {
		var qry = queryNew("foo", "varchar",[["foo1"],["foo2"]]);
		
		// jsonbColl is a QueryStruct, not a ordinary struct
		var jsonbColl = queryExecute("SELECT foo FROM qry", {}, {dbtype="query", returnType="struct", columnKey="foo"});

		cachePut("def",jsonbColl,createTimespan(0,0,0,30),createTimespan(0,0,0,30),"testCache4429")
		var cachedval = cacheGet(id ="def", region="testCache4429");

		// after cache the result should be a QueryStruct, not StructImpl
		expect(cachedval.getClass().getName()).toBe("lucee.runtime.type.query.QueryStruct"); 
		expect(cachedval.getRecordCount()).toBe(2); // should support method getRecordCount
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