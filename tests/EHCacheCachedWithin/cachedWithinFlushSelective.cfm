<cfscript>

function getCachedValue( id ) cachedwithin="#createTimeSpan( 0, 1, 0, 0 )#" {
	return createUUID();
}

function run() {
	var val1 = getCachedValue( 10 );
	var val2 = getCachedValue( 20 );

	if ( getCachedValue( 10 ) != val1 || getCachedValue( 20 ) != val2 )
		throw( message: "caching broken" );

	cachedWithinFlush( getCachedValue, [ 10 ] );

	if ( getCachedValue( 10 ) == val1 )
		throw( message: "arg 10 was not flushed" );

	if ( getCachedValue( 20 ) != val2 )
		throw( message: "arg 20 was incorrectly flushed" );

	echo( "PASS" );
}

run();

</cfscript>
