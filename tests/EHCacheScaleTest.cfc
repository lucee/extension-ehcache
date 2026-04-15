component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache" {

	public function beforeAll() {
		createCache();
	}

	public function run( testResults, testBox ) {

		describe( "EHCache Scale", function() {

			beforeEach( function() {
				cacheClear( "", "ehcacheScale" );
			});

			it( "handles 1000 entries", function() {
				loop from="1" to="1000" index="local.i" {
					cachePut( "scale_#i#", "value_#i#", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheScale" );
				}
				expect( cacheCount( "ehcacheScale" ) ).toBe( 1000 );
				expect( cacheGet( "scale_1", "ehcacheScale" ) ).toBe( "value_1" );
				expect( cacheGet( "scale_500", "ehcacheScale" ) ).toBe( "value_500" );
				expect( cacheGet( "scale_1000", "ehcacheScale" ) ).toBe( "value_1000" );
			});

			it( "lists 1000 keys", function() {
				loop from="1" to="1000" index="local.i" {
					cachePut( "ks_#i#", "v", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheScale" );
				}
				var ids = cacheGetAllIds( cacheName: "ehcacheScale" );
				expect( arrayLen( ids ) ).toBe( 1000 );
			});

			it( "clears 1000 entries", function() {
				loop from="1" to="1000" index="local.i" {
					cachePut( "cs_#i#", "v", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheScale" );
				}
				expect( cacheCount( "ehcacheScale" ) ).toBe( 1000 );
				cacheClear( "", "ehcacheScale" );
				expect( cacheCount( "ehcacheScale" ) ).toBe( 0 );
			});

			it( "caches a large value", function() {
				var bigVal = repeatString( "x", 100000 );
				cachePut( "bigValue", bigVal, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheScale" );
				var result = cacheGet( "bigValue", "ehcacheScale" );
				expect( len( result ) ).toBe( 100000 );
			});

			it( "evicts to disk when heap is full", function() {
				loop from="1" to="6000" index="local.i" {
					cachePut( "evict_#i#", "v_#i#", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheScale" );
				}
				expect( cacheCount( "ehcacheScale" ) ).toBe( 6000 );
				expect( cacheGet( "evict_1", "ehcacheScale" ) ).toBe( "v_1" );
				expect( cacheGet( "evict_6000", "ehcacheScale" ) ).toBe( "v_6000" );
			});

		});

	}

	private function createCache() {
		application action="update" name="ehcacheScaleTest" caches={
			"ehcacheScale": {
				class: "org.lucee.extension.cache.eh.EHCache",
				storage: false,
				custom: {
					"eternal": "false",
					"maxelementsinmemory": "5000",
					"memoryevictionpolicy": "LRU",
					"timeToIdleSeconds": "300",
					"timeToLiveSeconds": "300",
					"overflowtodisk": "true",
					"diskpersistent": "false",
					"maxelementsondisk": "100000",
					"distributed": "off"
				},
				default: ""
			}
		};
	}

}
