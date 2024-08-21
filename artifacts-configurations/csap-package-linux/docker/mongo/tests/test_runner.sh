#!/bin/bash

# $1 = run output logs
# $2 = METRICS_DB_SIZE 

GBytes=1024*1024*1024

# check changes to the script
metrics_set=`grep "db.createCollection( \"metrics\", { capped: true, size: $2\*GBytes" $1`
if [[ -z $metrics_set ]]; then
   echo "Failed config check should be $2"
   exit 1
fi

# check the collection status if DBs are capped (should be 2)
capped_count=`grep -c "\"capped\" : true" $1`
if [[ capped_count < 1 ]]; then
    echo "Failed capped check $capped_count"
    exit 1
fi

# check the collection status sizes
metrics_size=`grep "\"maxSize\" : NumberLong($(($2 * $GBytes)))" $1`
if [[ -z metrics_size ]]; then
    echo "Failed maxSize check"
    exit 1
fi
