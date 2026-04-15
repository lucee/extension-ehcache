component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache" {

	public function beforeAll() {
		createCache();
	}

	public function run( testResults, testBox ) {

		describe( "EHCache Filter Operations", function() {

			beforeEach( function() {
				cacheClear( "", "ehcacheFilter" );
			});

			it( "clears entries matching a wildcard", function() {
				cachePut( "user_1", "alice", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				cachePut( "user_2", "bob", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				cachePut( "product_1", "widget", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				cacheClear( "user_*", "ehcacheFilter" );
				expect( cacheIdExists( "user_1", "ehcacheFilter" ) ).toBeFalse();
				expect( cacheIdExists( "user_2", "ehcacheFilter" ) ).toBeFalse();
				expect( cacheIdExists( "product_1", "ehcacheFilter" ) ).toBeTrue();
			});

			it( "filters getAllIds with a wildcard", function() {
				cachePut( "alpha_1", "a", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				cachePut( "alpha_2", "b", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				cachePut( "beta_1", "c", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				var ids = cacheGetAllIds( "alpha_*", "ehcacheFilter" );
				expect( ids ).toBeArray();
				expect( arrayLen( ids ) ).toBe( 2 );
			});

			it( "filters getAll with a wildcard", function() {
				cachePut( "grp_a", "one", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				cachePut( "grp_b", "two", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				cachePut( "other_c", "three", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				var all = cacheGetAll( "grp_*", "ehcacheFilter" );
				expect( all ).toBeStruct();
				expect( structCount( all ) ).toBe( 2 );
			});

			it( "clears all with an empty filter", function() {
				cachePut( "ef_1", "a", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				cachePut( "ef_2", "b", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				cacheClear( "", "ehcacheFilter" );
				expect( cacheCount( "ehcacheFilter" ) ).toBe( 0 );
			});

			it( "does not clear when filter matches nothing", function() {
				cachePut( "keep_1", "a", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				cachePut( "keep_2", "b", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheFilter" );
				cacheClear( "nonexistent_*", "ehcacheFilter" );
				expect( cacheCount( "ehcacheFilter" ) ).toBe( 2 );
			});

		});

	}

	private function createCache() {
		application action="update" name="ehcacheFilterTest" caches={
			"ehcacheFilter": {
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
				default: ""
			}
		};
	}

}
