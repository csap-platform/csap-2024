
## Provides

[<img src="./images/agents.png" width="300" align="right"/>](./images/agents.png)

For each benchmark, an event is whatever is performed by the its execute_event method. For the CPU benchmark, it is cpu_execute_event() which is a loop calculating all prime numbers up to --cpu-max-prime.


### References: 
- [Reference Guide](https://github.com/akopytov/sysbench)
- [CPU notes](https://github.com/akopytov/sysbench/issues/140)
- [CPU Implementation](https://github.com/akopytov/sysbench/blob/master/src/tests/cpu/sb_cpu.c)
 
 
 &nbsp;
 
 &nbsp; 
 
 &nbsp; 
 
You can limit either the total number of events (in which case, you should be looking at total time to measure performance), or the total execution time (in which case, you should be looking at the number of events, because the total time is constant). sysbench 1.0 limits by the total time by default, older versions had a default limit on the number of events.


In both cases, latency stats give you an idea about performance. That's the time it took to calculate prime numbers up to --cpu-max-prime. For example, total time divided by the average latency will give you the total number of executed loops (aka events). 

### Configuration

Command Line parameters: 
- 
```sh
#
# a two minute run where the events will collected for comparison across infrastructure providers
#
docker run --name=sysbench-cpu --rm=true docker-public-artifactory.yourcompany.com/severalnines/sysbench \
  sysbench cpu --threads=2  --cpu-max-prime=100000 --time=120 run
```
 

Environment Variables For Start / Stop (csap-api.sh):
```yaml
timeToRunInSeconds: 60
threadIterations: "1 8"
```



Environment Variables For full runs (jobs - csap-tester.sh):
```yaml
timeToRunInSeconds: 120
threadIterations: "1 8"
```

