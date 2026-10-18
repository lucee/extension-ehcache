<cfscript>
component extends="org.lucee.cfml.test.LuceeTestCase" labels="ehcache" {

	function run( testResults, testBox ) {

		describe( "LDEV-6136 extension bundle name backwards compat", function() {


			// Lucee 8 is jakarta based, the old 2.10.0.39 OSGi bundle imports javax.transaction (LDEV-6613)
			it( title="works with old bundle name: org.lucee.ehcache.extension", skip=isLucee8(), body=function() {
				var cacheName = "ldev6136old";
				application action="update"
					caches="#{ "#cacheName#": cacheConfigFromBundle( "org.lucee.ehcache.extension" ,"2.10.0.39") }#";
				cachePut( id: "testNew", value: "new", cacheName: cacheName );
				expect( cacheGet( id: "testNew", cacheName: cacheName ) ).toBe( "new" );
			});
			// Lucee 8 resolves a version-less coordinate to the latest Central release (LDEV-6612)
			it( title="works with new maven name: org.lucee:ehcache", skip=isLucee8(), body=function() {
				var cacheName = "ldev6136new";
				application action="update"
					caches="#{ "#cacheName#": cacheConfigFromMaven( "org.lucee:ehcache" ) }#";
				cachePut( id: "testNew", value: "new", cacheName: cacheName );
				expect( cacheGet( id: "testNew", cacheName: cacheName ) ).toBe( "new" );
			});

		});

	}

	private struct function cacheConfigFromBundle( required string bundleName,  required string bundleVersion ) {
		return {
			class: 'org.lucee.extension.cache.eh.EHCache'
			, bundleName: arguments.bundleName
			, bundleVersion: arguments.bundleVersion
			, storage: false
			, custom: { "distributed": "off", "maxelementsinmemory": "1000" }
			, default: ''
		};
	}
	private struct function cacheConfigFromMaven( required string name ) {
		return {
			class: 'org.lucee.extension.cache.eh.EHCache'
			, maven: arguments.name
			, storage: false
			, custom: { "distributed": "off", "maxelementsinmemory": "1000" }
			, default: ''
		};
	}


	private boolean function isLucee8() {
		return listFirst( server.lucee.version, "." ) >= 8;
	}
}
</cfscript>
