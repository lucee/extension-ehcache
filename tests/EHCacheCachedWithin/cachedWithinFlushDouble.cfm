<cfscript>

function getCachedValue( id ) cachedwithin="#createTimeSpan( 0, 1, 0, 0 )#" {
	return createUUID();
}

function run() {
	getCachedValue( 999 );

	var r1 = cachedWithinFlush( getCachedValue, [ 999 ] );
	if ( !r1 )
		throw( message: "first flush should return true, got false" );

	var r2 = cachedWithinFlush( getCachedValue, [ 999 ] );
	if ( r2 )
		throw( message: "second flush should return false, got true" );

	echo( "PASS" );
}

run();

</cfscript>
