
## Provides

[<img src="./images/agents.png" width="300" align="right"/>](./images/agents.png)

- test platform for verifying mysql releases
- leverages sysbench for performing tests

 &nbsp;
 
 &nbsp;
 
This service leverages the csap docker integration to deploy and manage:
- mysql db: deployed as csap service, and creates schema for sysbench
- mysql admin: deployed as a csap service, phpmyadmin provides a UI for interacting with mysql
- sysbench: run as a csap job, using a docker container to initiate different run permutations


### References: 
- [sysbench introduction](https://www.howtoforge.com/how-to-benchmark-your-system-cpu-file-io-mysql-with-sysbench)
- [sysbench at Dockehub](https://hub.docker.com/r/severalnines/sysbench/)
- [sysbench github](https://github.com/akopytov/sysbench)
 
 
 &nbsp;
 
 &nbsp; 
 
 &nbsp; 
 
 

### Configuration

The examples below are for reference purposes only; they are defined using the [CSAP Definition](https://github.com/csap-platform/csap-core/wiki/Application-Definition)
```shell
#
# Note: Start up of the services
#
docker run --name db-server -e MYSQL_ROOT_PASSWORD=peter --network=dbnet -d  mysql:5.7
docker run --name phpmyadmin -d -p 8080:80 -e PMA_HOST=db-server --network=dbnet phpmyadmin

#
# Create the test table
#
docker run --rm=true --network=dbnet --name=sb-prepare \
  severalnines/sysbench sysbench --db-driver=mysql --oltp-table-size=100 --oltp-tables-count=24 \
  --threads=1 --mysql-host=db-server --mysql-port=3306 --mysql-user=sbtest --mysql-password=password \
  /usr/share/sysbench/tests/include/oltp_legacy/parallel_prepare.lua run

#
# Runs the test
#
docker run --name=sb-run --network=dbnet \
  severalnines/sysbench sysbench --db-driver=mysql --report-interval=2 --mysql-table-engine=innodb \
  --oltp-table-size=100000 --oltp-tables-count=24 --threads=64 --time=99999 \
  --mysql-host=db-server --mysql-port=3306 \
  --mysql-user=sbtest --mysql-password=password \
  /usr/share/sysbench/tests/include/oltp_legacy/oltp.lua run
```
 

Environment Variables:
```json
{
	"var1": "value1",
	"var2": "value2",
}
```



