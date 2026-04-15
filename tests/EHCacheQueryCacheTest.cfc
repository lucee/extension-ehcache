component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache" {

	public function beforeAll() {
		createCache();
		cacheClear( "", "ehcacheQueryCache" );
	}

	public function run( testResults, testBox ) {

		describe( "EHCache Query Object Serialization", function() {

			beforeEach( function() {
				cacheClear( "", "ehcacheQueryCache" );
			});

			it( "caches a query object", function() {
				var q = queryNew( "id,val", "integer,varchar", [ [ 1, createUUID() ] ] );
				cachePut( "qcache_test", q, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheQueryCache" );
				var cached = cacheGet( "qcache_test", "ehcacheQueryCache" );
				expect( isQuery( cached ) ).toBeTrue();
				expect( cached.recordCount ).toBe( 1 );
				expect( cached.val[ 1 ] ).toBe( q.val[ 1 ] );
			});

			it( "returns cached query on second get", function() {
				var uuid = createUUID();
				var q = queryNew( "id,val", "integer,varchar", [ [ 1, uuid ] ] );
				cachePut( "qcache_hit", q, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheQueryCache" );
				var first = cacheGet( "qcache_hit", "ehcacheQueryCache" );
				var second = cacheGet( "qcache_hit", "ehcacheQueryCache" );
				expect( first.val[ 1 ] ).toBe( uuid );
				expect( second.val[ 1 ] ).toBe( uuid );
			});

			it( "caches a multi-row query", function() {
				var q = queryNew( "id,name", "integer,varchar" );
				loop from="1" to="100" index="local.i" {
					queryAddRow( q, { id: i, name: "row_#i#" } );
				}
				cachePut( "qcache_big", q, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheQueryCache" );
				var cached = cacheGet( "qcache_big", "ehcacheQueryCache" );
				expect( isQuery( cached ) ).toBeTrue();
				expect( cached.recordCount ).toBe( 100 );
				expect( cached.name[ 50 ] ).toBe( "row_50" );
			});

			it( "caches a query with multiple column types", function() {
				var q = queryNew(
					"id,name,score,active,created",
					"integer,varchar,double,bit,timestamp",
					[ [ 1, "alice", 95.5, true, now() ] ]
				);
				cachePut( "qcache_types", q, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheQueryCache" );
				var cached = cacheGet( "qcache_types", "ehcacheQueryCache" );
				expect( isQuery( cached ) ).toBeTrue();
				expect( cached.name[ 1 ] ).toBe( "alice" );
				expect( cached.score[ 1 ] ).toBe( 95.5 );
				expect( cached.active[ 1 ] ).toBeTrue();
			});

		});

	}

	private function createCache() {
		application action="update" name="ehcacheQueryCacheTest" caches={
			"ehcacheQueryCache": {
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
