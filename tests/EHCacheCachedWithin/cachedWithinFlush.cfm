<cfscript>

function getCachedValue( id ) cachedwithin="#createTimeSpan( 0, 1, 0, 0 )#" {
	return createUUID();
}

function run() {
	var first = getCachedValue( 1 );
	var second = getCachedValue( 1 );

	if ( first != second )
		throw( message: "caching broken: first != second" );

	var flushed = cachedWithinFlush( getCachedValue, [ 1 ] );
	if ( !flushed )
		throw( message: "cachedWithinFlush returned false" );

	var third = getCachedValue( 1 );
	if ( third == first )
		throw( message: "value not flushed: third == first" );

	echo( "PASS" );
}

run();

</cfscript>
