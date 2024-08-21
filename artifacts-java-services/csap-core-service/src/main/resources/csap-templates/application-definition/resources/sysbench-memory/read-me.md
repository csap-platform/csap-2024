
## Provides

[<img src="./images/agents.png" width="300" align="right"/>](./images/agents.png)

For each benchmark, an event is whatever is performed by the its execute_event method. For the Memory benchmark, it is memory_execute_event() which performs memory access tests (see below).


### References: 
- [Reference Guide](https://github.com/akopytov/sysbench)
- [Notes](https://github.com/akopytov/sysbench/issues/233)
- [Memory Implementation](https://github.com/akopytov/sysbench/blob/master/src/tests/memory/sb_memory.c)
 
 
 &nbsp;
 
 &nbsp; 
 
 &nbsp; 
 
You can limit either the total number of events (in which case, you should be looking at total time to measure performance), or the total execution time (in which case, you should be looking at the number of events, because the total time is constant). sysbench 1.0 limits by the total time by default, older versions had a default limit on the number of events.


In both cases, latency stats give you an idea about performance. That's the time it took to read and write a block of memory of size --memory-block-size. For example, total time divided by the average latency will give you the total number of executed loops (aka events). 

### Configuration

Command Line parameters: 
- 
```sh
#
# Output from sysbench memory help
#
memory options:
  --memory-block-size=SIZE    size of memory block for test [1K]
  --memory-total-size=SIZE    total size of data to transfer [100G]
  --memory-scope=STRING       memory access scope {global,local} [global]
  --memory-hugetlb[=on|off]   allocate memory from HugeTLB pool [off]
  --memory-oper=STRING        type of memory operations {read, write, none} [write]
  --memory-access-mode=STRING memory access mode {seq,rnd} [seq]


#
# a two minute run where the events will collected for comparison across infrastructure providers
#
docker run --name=sysbench-memory --rm=true docker-public-artifactory.yourcompany.com/severalnines/sysbench \
  sysbench memory --threads=2  --time=120 --memory-scope=global --memory-access-mode=rnd  --report-interval=0 run
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

