component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache" {

	public function beforeAll() {
		createCache();
	}

	public function run( testResults, testBox ) {

		describe( "CacheDelete", function() {

			beforeEach( function() {
				cacheClear( "", "ehcacheFuncs" );
			});

			it( "deletes an existing entry", function() {
				cachePut( "delMe", "val", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFuncs" );
				expect( cacheIdExists( "delMe", "ehcacheFuncs" ) ).toBeTrue();
				cacheDelete( id: "delMe", cacheName: "ehcacheFuncs" );
				expect( cacheIdExists( "delMe", "ehcacheFuncs" ) ).toBeFalse();
			});

			it( "throws when deleting a missing entry with throwOnError", function() {
				expect( function() {
					cacheDelete( id: "nope_#createUUID()#", throwOnError: true, cacheName: "ehcacheFuncs" );
				}).toThrow();
			});

			it( "does not throw when deleting a missing entry without throwOnError", function() {
				cacheDelete( id: "nope_#createUUID()#", throwOnError: false, cacheName: "ehcacheFuncs" );
			});

		});

		describe( "CacheGetProperties", function() {

			it( "returns an array for object type", function() {
				var props = cacheGetProperties( "object" );
				expect( props ).toBeArray();
			});

			it( "returns an array with no args", function() {
				var props = cacheGetProperties();
				expect( props ).toBeArray();
			});

		});

		describe( "CacheGetDefaultCacheName", function() {

			it( "returns the default object cache name", function() {
				var name = cacheGetDefaultCacheName( "object" );
				expect( name ).toBeString();
				expect( len( name ) ).toBeGT( 0 );
			});

			it( "returns the default query cache name when set", function() {
				var name = cacheGetDefaultCacheName( "query" );
				expect( name ).toBeString();
				expect( len( name ) ).toBeGT( 0 );
			});

		});

		describe( "CacheClear on named cache", function() {

			it( "clears all entries in a named cache", function() {
				cachePut( "scc1", "a", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFuncs" );
				expect( cacheCount( "ehcacheFuncs" ) ).toBeGTE( 1 );
				cacheClear( "", "ehcacheFuncs" );
				expect( cacheCount( "ehcacheFuncs" ) ).toBe( 0 );
			});

		});

	}

	private function createCache() {
		application action="update" name="ehcacheFuncsTest" caches={
			"ehcacheFuncs": {
				class: "org.lucee.extension.cache.eh.EHCache",
				storage: false,
				custom: {
					"eternal": "false",
					"maxelementsinmemory": "1000",
					"memoryevictionpolicy": "LRU",
					"timeToIdleSeconds": "300",
					"timeToLiveSeconds": "300",
					"overflowtodisk": "false",
					"diskpersistent": "false",
					"maxelementsondisk": "0",
					"distributed": "off"
				},
				default: "object"
			},
			"ehcacheQuery": {
				class: "org.lucee.extension.cache.eh.EHCache",
				storage: false,
				custom: {
					"eternal": "false",
					"maxelementsinmemory": "1000",
					"memoryevictionpolicy": "LRU",
					"timeToIdleSeconds": "300",
					"timeToLiveSeconds": "300",
					"overflowtodisk": "false",
					"diskpersistent": "false",
					"maxelementsondisk": "0",
					"distributed": "off"
				},
				default: "query"
			}
		};
	}

}
