<cfsetting showdebugoutput="false"><cfscript>
ts = now(); 
// client
if(isNull(client.startTime))client.startTime=ts;
if(!isNull(client.time)) client.lastTime=client.time;
client.time=ts;
// session
if(isNull(session.startTime))session.startTime=ts;
if(!isNull(session.time)) session.lastTime=session.time;
session.time=ts;



echo(serialize({client:client,session:session}));



</cfscript>