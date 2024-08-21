sysbench 1.0.17 (using bundled LuaJIT 2.1.0-beta2)

Running the test with following options:
Number of threads: 64
Report intermediate results every 5 second(s)
Initializing random number generator from current time


Initializing worker threads...

Threads started!

[ 5s ] thds: 64 tps: 1992.80 qps: 40061.40 (r/w/o: 28078.39/7984.61/3998.40) lat (ms,95%): 8.58 err/s: 0.00 reconn/s: 0.00
[ 10s ] thds: 64 tps: 1044.18 qps: 20921.47 (r/w/o: 14618.57/4214.53/2088.37) lat (ms,95%): 15.55 err/s: 0.00 reconn/s: 0.00
[ 15s ] thds: 64 tps: 6589.62 qps: 131666.89 (r/w/o: 92174.74/26313.10/13179.05) lat (ms,95%): 11.24 err/s: 0.00 reconn/s: 0.00
[ 20s ] thds: 64 tps: 9849.92 qps: 197002.64 (r/w/o: 137899.91/39402.69/19700.04) lat (ms,95%): 11.24 err/s: 0.00 reconn/s: 0.00
[ 25s ] thds: 64 tps: 9778.44 qps: 195563.10 (r/w/o: 136895.03/39111.38/19556.69) lat (ms,95%): 11.45 err/s: 0.00 reconn/s: 0.00
[ 30s ] thds: 64 tps: 9463.82 qps: 189274.52 (r/w/o: 132491.43/37855.66/18927.43) lat (ms,95%): 12.08 err/s: 0.00 reconn/s: 0.00
[ 35s ] thds: 64 tps: 9113.86 qps: 182328.10 (r/w/o: 127634.37/36466.42/18227.31) lat (ms,95%): 12.98 err/s: 0.00 reconn/s: 0.00
[ 40s ] thds: 64 tps: 8614.40 qps: 172305.08 (r/w/o: 120622.66/34452.82/17229.61) lat (ms,95%): 13.46 err/s: 0.00 reconn/s: 0.00
[ 45s ] thds: 64 tps: 8171.49 qps: 163429.66 (r/w/o: 114399.10/32687.57/16342.99) lat (ms,95%): 14.46 err/s: 0.00 reconn/s: 0.00
[ 50s ] thds: 64 tps: 2591.59 qps: 51848.14 (r/w/o: 36296.22/10368.75/5183.17) lat (ms,95%): 14.46 err/s: 0.00 reconn/s: 0.00
[ 55s ] thds: 64 tps: 335.35 qps: 6679.45 (r/w/o: 4673.13/1336.01/670.30) lat (ms,95%): 450.77 err/s: 0.00 reconn/s: 0.00
[ 60s ] thds: 64 tps: 3232.17 qps: 64636.57 (r/w/o: 45241.16/12931.47/6463.94) lat (ms,95%): 14.73 err/s: 0.00 reconn/s: 0.00
SQL statistics:
    queries performed:
        read:                            4955496
        write:                           1415856
        other:                           707928
        total:                           7079280
    transactions:                        353964 (5898.32 per sec.)
    queries:                             7079280 (117966.44 per sec.)
    ignored errors:                      0      (0.00 per sec.)
    reconnects:                          0      (0.00 per sec.)

General statistics:
    total time:                          60.0092s
    total number of events:              353964

Latency (ms):
         min:                                    2.24
         avg:                                   10.85
         max:                                 8150.81
         95th percentile:                       12.75
         sum:                              3839308.14

Threads fairness:
    events (avg/stddev):           5530.6875/175.28
    execution time (avg/stddev):   59.9892/0.00

