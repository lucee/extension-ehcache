component {
	this.name = 'AppA549';
	this.cache.connections["default"] = {
	  class: 'org.lucee.extension.cache.eh.EHCache'
	, maven: ehcacheMaven()
	, storage: true
	, custom: {"bootstrapAsynchronously":"true","replicatePuts":"true","automatic_hostName":"","bootstrapType":"on","maxelementsinmemory":"10000","manual_rmiUrls":"","distributed":"off","automatic_multicastGroupAddress":"230.0.0.1","memoryevictionpolicy":"LRU","replicatePutsViaCopy":"true","timeToIdleSeconds":"86400","maximumChunkSizeBytes":"5000000","automatic_multicastGroupPort":"4446","listener_socketTimeoutMillis":"120000","timeToLiveSeconds":"86400","diskpersistent":"true","manual_addional":"","replicateRemovals":"true","replicateUpdatesViaCopy":"true","automatic_addional":"","overflowtodisk":"true","replicateAsynchronously":"true","maxelementsondisk":"10000000","listener_remoteObjectPort":"","asynchronousReplicationIntervalMillis":"1000","listener_hostName":"","replicateUpdates":"true","manual_hostName":"","automatic_timeToLive":"unrestricted","listener_port":""}
	, default: 'object'
	};
	this.cache.object = "default";

	public function onRequestStart() {
		setting requesttimeout=10;
	}

	// LDEV-6613 use the installed extension version: on Lucee 8 a maven coordinate without
	// a version resolves the latest Central release (LDEV-6612), not the installed extension
	private string function ehcacheMaven() {
		var q = extensionList();
		loop query=q {
			if ( q.id == "87FE44E5-179C-43A3-A87B3D38BEF4652E" ) return "org.lucee:ehcache:" & q.version;
		}
		return "org.lucee:ehcache";
	}
}