component {

	this.name = "ehcacheCachedWithinTest-#hash( getCurrentTemplatePath() )#";

	this.cache.function = {
		class: "org.lucee.extension.cache.eh.EHCache",
		storage: false,
		custom: {
			"eternal": "false",
			"maxelementsinmemory": "1000",
			"memoryevictionpolicy": "LRU",
			"timeToIdleSeconds": "86400",
			"timeToLiveSeconds": "86400",
			"overflowtodisk": "false",
			"diskpersistent": "false",
			"maxelementsondisk": "0",
			"distributed": "off"
		}
	};

}
