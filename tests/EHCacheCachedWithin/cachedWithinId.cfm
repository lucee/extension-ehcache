<cfscript>

function getCachedValue( id ) cachedwithin="#createTimeSpan( 0, 1, 0, 0 )#" {
	return createUUID();
}

function run() {
	getCachedValue( 1 );

	var idArr = cachedWithinId( getCachedValue, [ 1 ] );
	if ( !isSimpleValue( idArr ) || len( idArr ) == 0 )
		throw( message: "array args: expected non-empty string, got [#idArr#]" );

	var idStruct = cachedWithinId( getCachedValue, { id: 1 } );
	if ( idArr != idStruct )
		throw( message: "array vs struct mismatch: [#idArr#] != [#idStruct#]" );

	echo( "PASS" );
}

run();

</cfscript>
