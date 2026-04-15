component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache" {

	public function beforeAll() {
		createCache();
	}

	public function run( testResults, testBox ) {

		describe( "EHCache Type Serialization", function() {

			beforeEach( function() {
				cacheClear( "", "ehcacheTypes" );
			});

			it( "caches a string", function() {
				cachePut( "typeStr", "hello world", createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheTypes" );
				var result = cacheGet( "typeStr", "ehcacheTypes" );
				expect( result ).toBe( "hello world" );
				expect( isSimpleValue( result ) ).toBeTrue();
			});

			it( "caches a number", function() {
				cachePut( "typeNum", 42.5, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheTypes" );
				expect( cacheGet( "typeNum", "ehcacheTypes" ) ).toBe( 42.5 );
			});

			it( "caches a boolean", function() {
				cachePut( "typeBool", true, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheTypes" );
				expect( cacheGet( "typeBool", "ehcacheTypes" ) ).toBeTrue();
			});

			it( "caches a date", function() {
				var dt = now();
				cachePut( "typeDate", dt, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheTypes" );
				expect( dateCompare( cacheGet( "typeDate", "ehcacheTypes" ), dt ) ).toBe( 0 );
			});

			it( "caches a struct", function() {
				var data = { name: "Zac", role: "dev", score: 99 };
				cachePut( "typeStruct", data, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheTypes" );
				var result = cacheGet( "typeStruct", "ehcacheTypes" );
				expect( result ).toBeStruct();
				expect( result.name ).toBe( "Zac" );
				expect( result.role ).toBe( "dev" );
				expect( result.score ).toBe( 99 );
			});

			it( "caches an array", function() {
				var data = [ "a", "b", "c", 1, 2, 3 ];
				cachePut( "typeArr", data, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheTypes" );
				var result = cacheGet( "typeArr", "ehcacheTypes" );
				expect( result ).toBeArray();
				expect( arrayLen( result ) ).toBe( 6 );
				expect( result[ 1 ] ).toBe( "a" );
				expect( result[ 6 ] ).toBe( 3 );
			});

			it( "caches a query", function() {
				var qry = queryNew( "id,name", "integer,varchar", [ [ 1, "alpha" ], [ 2, "bravo" ], [ 3, "charlie" ] ] );
				cachePut( "typeQuery", qry, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheTypes" );
				var result = cacheGet( "typeQuery", "ehcacheTypes" );
				expect( isQuery( result ) ).toBeTrue();
				expect( result.recordCount ).toBe( 3 );
				expect( result.name[ 2 ] ).toBe( "bravo" );
			});

			// LDEV-4498 regression
			it( "preserves ordered struct key order", function() {
				var data = structNew( "ordered" );
				data[ "second" ] = 2;
				data[ "first" ] = 1;
				data[ "third" ] = 3;
				cachePut( "typeOrdered", data, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheTypes" );
				var result = cacheGet( "typeOrdered", "ehcacheTypes" );
				expect( result ).toBeStruct();
				var keys = structKeyArray( result );
				expect( keys[ 1 ] ).toBe( "second" );
				expect( keys[ 2 ] ).toBe( "first" );
				expect( keys[ 3 ] ).toBe( "third" );
			});

			it( "caches nested complex values", function() {
				var data = {
					users: [
						{ name: "Alice", tags: [ "admin", "dev" ] },
						{ name: "Bob", tags: [ "user" ] }
					],
					meta: { count: 2, active: true }
				};
				cachePut( "typeNested", data, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheTypes" );
				var result = cacheGet( "typeNested", "ehcacheTypes" );
				expect( result.users ).toBeArray();
				expect( arrayLen( result.users ) ).toBe( 2 );
				expect( result.users[ 1 ].name ).toBe( "Alice" );
				expect( result.users[ 1 ].tags[ 2 ] ).toBe( "dev" );
				expect( result.meta.count ).toBe( 2 );
				expect( result.meta.active ).toBeTrue();
			});

			it( "caches binary data", function() {
				var data = toBinary( toBase64( "binary content here" ) );
				cachePut( "typeBinary", data, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheTypes" );
				var result = cacheGet( "typeBinary", "ehcacheTypes" );
				expect( isBinary( result ) ).toBeTrue();
				expect( toString( result ) ).toBe( "binary content here" );
			});

			// LDEV-4429 regression
			it( "caches a QueryStruct", function() {
				var qry = queryNew( "foo", "varchar", [ [ "foo1" ], [ "foo2" ] ] );
				var qs = queryExecute( "SELECT foo FROM qry", {}, { dbtype: "query", returnType: "struct", columnKey: "foo" } );
				cachePut( "typeQueryStruct", qs, createTimespan( 0, 0, 5, 0 ), createTimespan( 0, 0, 5, 0 ), "ehcacheTypes" );
				var result = cacheGet( "typeQueryStruct", "ehcacheTypes" );
				expect( result.getClass().getName() ).toBe( "lucee.runtime.type.query.QueryStruct" );
				expect( result.getRecordCount() ).toBe( 2 );
			});

		});

	}

	private function createCache() {
		application action="update" name="ehcacheTypesTest" caches={
			"ehcacheTypes": {
				class: "org.lucee.extension.cache.eh.EHCache",
				storage: false,
				custom: {
					"eternal": "false",
					"maxelementsinmemory": "1000",
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
