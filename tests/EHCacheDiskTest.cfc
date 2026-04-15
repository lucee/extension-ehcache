component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache" {

	public function beforeAll() {
		createCache();
	}

	public function run( testResults, testBox ) {

		describe( "EHCache Disk Overflow", function() {

			beforeEach( function() {
				cacheClear( "", "ehcacheDisk" );
			});

			it( "overflows to disk when heap is full", function() {
				loop from="1" to="20" index="local.i" {
					cachePut( "disk_#i#", "value_#i#", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheDisk" );
				}
				loop from="1" to="20" index="local.i" {
					expect( cacheGet( "disk_#i#", "ehcacheDisk" ) ).toBe( "value_#i#" );
				}
				expect( cacheCount( "ehcacheDisk" ) ).toBe( 20 );
			});

			it( "serializes complex values to disk", function() {
				// fill heap so complex value gets pushed to disk
				loop from="1" to="10" index="local.i" {
					cachePut( "filler_#i#", "x", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheDisk" );
				}
				var data = { name: "test", items: [ 1, 2, 3 ], nested: { deep: true } };
				cachePut( "complexDisk", data, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheDisk" );
				var result = cacheGet( "complexDisk", "ehcacheDisk" );
				expect( result ).toBeStruct();
				expect( result.name ).toBe( "test" );
				expect( result.items ).toBeArray();
				expect( result.nested.deep ).toBeTrue();
			});

			it( "includes disk entries in count", function() {
				loop from="1" to="15" index="local.i" {
					cachePut( "cnt_#i#", "v", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheDisk" );
				}
				expect( cacheCount( "ehcacheDisk" ) ).toBe( 15 );
			});

			it( "includes disk entries in key listing", function() {
				loop from="1" to="15" index="local.i" {
					cachePut( "dkey_#i#", "v", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheDisk" );
				}
				var ids = cacheGetAllIds( cacheName: "ehcacheDisk" );
				expect( arrayLen( ids ) ).toBe( 15 );
			});

			it( "can remove entries from disk", function() {
				loop from="1" to="15" index="local.i" {
					cachePut( "rm_#i#", "v", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheDisk" );
				}
				cacheRemove( "rm_1", false, "ehcacheDisk" );
				expect( cacheIdExists( "rm_1", "ehcacheDisk" ) ).toBeFalse();
				expect( cacheCount( "ehcacheDisk" ) ).toBe( 14 );
			});

			it( "clears both heap and disk", function() {
				loop from="1" to="15" index="local.i" {
					cachePut( "clr_#i#", "v", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheDisk" );
				}
				cacheClear( "", "ehcacheDisk" );
				expect( cacheCount( "ehcacheDisk" ) ).toBe( 0 );
			});

		});

	}

	private function createCache() {
		application action="update" name="ehcacheDiskTest" caches={
			"ehcacheDisk": {
				class: "org.lucee.extension.cache.eh.EHCache",
				storage: false,
				custom: {
					"eternal": "false",
					"maxelementsinmemory": "10",
					"memoryevictionpolicy": "LRU",
					"timeToIdleSeconds": "300",
					"timeToLiveSeconds": "300",
					"overflowtodisk": "true",
					"diskpersistent": "false",
					"maxelementsondisk": "10000",
					"distributed": "off"
				},
				default: ""
			}
		};
	}

}
