//
// New DB setup only: typical migrations skip this as replica configuration will sync from an in service peer
//

db = db.getSiblingDB("metricsDb");


//creates capped collection with size in bytes spec   1099511627776
var GBytes=1024*1024*1024 ;
db.createCollection( "metrics", { capped: true, size: AA_REPLACE_METRICS_DB_SIZE*GBytes } );

//creates index with specified values. background true helps in not stopping all other operations
db.metrics.createIndex({"attributes.hostName":1,"attributes.id":1,"createdOn.lastUpdatedOn":-1},{ background: true });
db.metrics.createIndex({"attributes.hostName":1,"createdOn.date":1},{ background: true });
db.metrics.createIndex({"createdOn.date":1},{ background: true });

db.createCollection("metricsAttributes");
db.metricsAttributes.ensureIndex({"hostName": 1, "id": 1, "createdOn.lastUpdatedOn": 1}, {"background": true});

// dump collection info
printjson(db.metrics.stats());
printjson(db.metricsAttributes.stats());


db = db.getSiblingDB("event");

// capped collect prevents updates to health, and reporting data updated every 15 minutes.
// Ideally - these will be moved to a separate "daily" collection, and aggregated in real time 
db.createCollection( "eventRecords");
db.eventRecords.ensureIndex({"lifecycle":1});
db.eventRecords.ensureIndex({"summary":1});
db.eventRecords.ensureIndex({"appId":1});
db.eventRecords.ensureIndex({"project":1});
db.eventRecords.ensureIndex({"host":1});
db.eventRecords.ensureIndex({"category":1});

// index for typical user history https://docs.mongodb.org/manual/core/index-compound/
db.eventRecords.ensureIndex({"metaData.uiUser":1,"createdOn.date":-1}, {"background": true});

db.eventRecords.ensureIndex({"metaData.uiUser":1});
db.eventRecords.ensureIndex({"createdOn.lastUpdatedOn":-1});
db.eventRecords.ensureIndex({"appId":1,"lifecycle":1});
db.eventRecords.ensureIndex({"category":1,"summary":1,"metaData.uiUser":1});
db.eventRecords.ensureIndex({"appId":1,"createdOn.mongoDate":-1});
//expireAfterSeconds 0 means it will expire at the time specified by the expiresAt attribute
db.eventRecords.createIndex( { "expireAt": 1 }, { expireAfterSeconds: 0 } );

db.eventRecords.createIndex({"appId":1,"project":1,"lifecycle":1,"createdOn.lastUpdatedOn":-1})
db.eventRecords.createIndex({"appId":1,"project":1,"lifecycle":1,"data.summary.serviceName":1})
db.eventRecords.ensureIndex({"createdOn.date":-1});


// index for trending
db.eventRecords.createIndex({"appId":1,"project":1,"lifecycle":1,"category":1,"createdOn.date":-1})
//db.eventRecords.createIndex({"appId":1,"lifecycle":1,"category":1,"createdOn.lastUpdatedOn":-1})


// index for event browser search dialpg
db.eventRecords.createIndex({"appId":1,"lifecycle":1,"createdOn.date":-1})
db.eventRecords.createIndex({"appId":1, "lifecycle":1,"createdOn.date":-1, "host":1 })

// index for health events
db.eventRecords.createIndex({"category":1,"host":1,"createdOn.date":-1})
 
// dump collecton info
printjson(db.eventRecords.stats());

printjson(db.getProfilingStatus()) ;

print("\n\nUpdating slow query profile 0 to 1000\n\n");
db.setProfilingLevel(0, { slowms: 1000 }) ;

print("After updating");
printjson(db.getProfilingStatus()) ;
