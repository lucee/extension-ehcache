component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache" {

	public function beforeAll() {
		createCache();
	}

	public function run( testResults, testBox ) {

		describe( "EHCache Concurrency", function() {

			beforeEach( function() {
				cacheClear( "", "ehcacheConcurrent" );
			});

			it( "handles concurrent puts without errors", function() {
				var threads = {};
				loop from="1" to="10" index="local.i" {
					var tName = "cput_#i#";
					threads[ tName ] = tName;
					thread name="#tName#" i="#i#" {
						loop from="1" to="50" index="local.j" {
							cachePut( "conc_#i#_#j#", "val_#i#_#j#", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheConcurrent" );
						}
					}
				}
				for ( var t in threads ) {
					thread action="join" name="#t#" timeout="30000";
				}
				for ( var t in threads ) {
					if ( structKeyExists( cfthread[ t ], "error" ) ) {
						fail( "Thread #t# errored: #cfthread[ t ].error.message#" );
					}
				}
				expect( cacheCount( "ehcacheConcurrent" ) ).toBe( 500 );
			});

			it( "handles concurrent reads and writes without errors", function() {
				// seed data
				loop from="1" to="100" index="local.i" {
					cachePut( "rw_#i#", "seed_#i#", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheConcurrent" );
				}
				var threads = {};
				// writers
				loop from="1" to="5" index="local.i" {
					var tName = "writer_#i#";
					threads[ tName ] = tName;
					thread name="#tName#" i="#i#" {
						loop from="1" to="100" index="local.j" {
							cachePut( "rw_#j#", "updated_#i#_#j#", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheConcurrent" );
						}
					}
				}
				// readers
				loop from="1" to="5" index="local.i" {
					var tName = "reader_#i#";
					threads[ tName ] = tName;
					thread name="#tName#" i="#i#" {
						loop from="1" to="100" index="local.j" {
							cacheGet( "rw_#j#", "ehcacheConcurrent" );
						}
					}
				}
				for ( var t in threads ) {
					thread action="join" name="#t#" timeout="30000";
				}
				for ( var t in threads ) {
					if ( structKeyExists( cfthread[ t ], "error" ) ) {
						fail( "Thread #t# errored: #cfthread[ t ].error.message#" );
					}
				}
			});

			it( "handles concurrent clears without errors", function() {
				loop from="1" to="50" index="local.i" {
					cachePut( "cc_#i#", "v", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheConcurrent" );
				}
				var threads = {};
				loop from="1" to="5" index="local.i" {
					var tName = "clr_#i#";
					threads[ tName ] = tName;
					thread name="#tName#" {
						cacheClear( "", "ehcacheConcurrent" );
					}
				}
				for ( var t in threads ) {
					thread action="join" name="#t#" timeout="30000";
				}
				for ( var t in threads ) {
					if ( structKeyExists( cfthread[ t ], "error" ) ) {
						fail( "Thread #t# errored: #cfthread[ t ].error.message#" );
					}
				}
				expect( cacheCount( "ehcacheConcurrent" ) ).toBe( 0 );
			});

		});

	}

	private function createCache() {
		application action="update" name="ehcacheConcurrencyTest" caches={
			"ehcacheConcurrent": {
				class: "org.lucee.extension.cache.eh.EHCache",
				storage: false,
				custom: {
					"eternal": "false",
					"maxelementsinmemory": "10000",
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
