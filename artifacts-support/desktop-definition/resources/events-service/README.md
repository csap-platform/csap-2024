## Provides
[<img src="./images/portals.png" width="300" align="right"/>](./images/portals.png)
CSAP event services provide both management activity (who did what, when) and performance activity (cpu used, load, message latency, ...)
It also provides the CSAP adoption portal which  provides historical data feeds for the management portals, as well as adoption reports.


### Recovery
```bash
#
# In case of a non graceful shutdown, mongo storage folder may need to be recovered
#
# alternate: /data/csap-storage/events-mongo-volume , or user=1004
docker run -it --user=csap -v /perfshared/csap/events-mongo-volume:/data/db docker-dev-artifactory.yourcompany.com/csapplatform/mongo:21.08  mongod --repair

```

 
### References
- [CSAP Event Wiki](https://github.com/csap-platform/csap-event-services/wiki),
- [Spring Boot Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html)

 
 &nbsp;
 
 &nbsp; 
 
 &nbsp; 
 
 
## Configuration

The following environment variables are required:
```json
	"csap-events-defaults": {
		"mongoUser": "dataBaseReadWriteUser",
		"mongoPassword": "xxxx",
		"mongoHosts": "$$csap-hosts:events-mongo",
		"$$mongo-storage": "/perfshared/csap/$$service-name-volume",
		"restToken": "csap-performance-app",
		"dataServiceUser": "csap-performance-app",
		"dataServicePass": "xxxx",
		"metricDbSizeInGb": 5,
		"eventDbSizeInGb": 1,
		"MONGO_UTIL": "csapplatform/mongo:21.08",
		"MONGO_INITDB_ROOT_USERNAME": "dataBaseReadWriteUser",
		"MONGO_INITDB_ROOT_PASSWORD": "xxxxx",
		"found-in": [
			"events-mongo",
			"events-service"
		]
	}
```

 
 &nbsp;
 
 &nbsp; 
 
 &nbsp; 
 
 

### Desktop development:
- Some tests require provisioned systems, such as LDAP, git, etc.
	- **application-company.yml**  is a small subset useful for quickly getting started
- refer to application.yml and application-company.yml for complete set of variables

- dependencies defined using maven, so any IDE works  
- create csap folder in your home directory, copy and modify
	- csapSecurity.properties
	- application-company.yml
- add the following parameter to your IDE start command 
	- ```--spring.config.location=file:c:/Users/yourHomeDir/csap/```
- add the following parameter to your JVM properties
	- ```-DcsapTest=/Users/yourHomeDir/csap/```

### Unit tests
- add the following to your env: ```-DcsapTest="/Users/yourHomeDir/csap/"```


 
 &nbsp;
 
 &nbsp; 
 
 &nbsp; 
 
 