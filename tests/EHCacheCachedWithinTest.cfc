component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache" {

	// cachedWithinFlush / cachedWithinId require >= 7.0.2.26
	// tests use _InternalRequest into a sub-application so the BIFs
	// are called directly (evaluate() changes UDF page context, breaking cache key identity)

	private boolean function skipCachedWithin() {
		try {
			return !server.checkVersionGTE( server.lucee.version, 7, 0, 2, 26 );
		} catch ( any e ) {
			return true;
		}
	}

	function beforeAll() {
		variables.uri = createURI( "EHCacheCachedWithin" );
	}

	public function run( testResults, testBox ) {

		describe( "CachedWithinId with EHCache", function() {

			it( title: "returns a cache id for a cached function", skip: skipCachedWithin(), body: function() {
				local.result = _InternalRequest( template: "#uri#/cachedWithinId.cfm" );
				expect( result.filecontent.trim() ).toBe( "PASS" );
			});

		});

		describe( "CachedWithinFlush with EHCache", function() {

			it( title: "flushes a cached function result", skip: skipCachedWithin(), body: function() {
				local.result = _InternalRequest( template: "#uri#/cachedWithinFlush.cfm" );
				expect( result.filecontent.trim() ).toBe( "PASS" );
			});

			it( title: "selectively flushes only the targeted arguments", skip: skipCachedWithin(), body: function() {
				local.result = _InternalRequest( template: "#uri#/cachedWithinFlushSelective.cfm" );
				expect( result.filecontent.trim() ).toBe( "PASS" );
			});

			it( title: "returns false when flushing already-flushed entry", skip: skipCachedWithin(), body: function() {
				local.result = _InternalRequest( template: "#uri#/cachedWithinFlushDouble.cfm" );
				expect( result.filecontent.trim() ).toBe( "PASS" );
			});

		});

	}

	private string function createURI( string calledName ) {
		var baseURI = contractPath( getDirectoryFromPath( getCurrenttemplatepath() ) );
		return baseURI & "/" & calledName;
	}

}
