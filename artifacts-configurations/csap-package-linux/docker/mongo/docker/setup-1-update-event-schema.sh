#!/bin/bash

LINE="_______________________________________________________________________________________________\n"
function print_with_head() { 
	echo -e "\n$LINE \n  $* \n$LINE"; 
}
function printLine() { print_line $* ; }

metricDbSizeInGb=${metricDbSizeInGb:-20} ;
schemaFile=${schemaFile:-/docker-entrypoint-initdb.d/setup-2-event-schema.js} ;

print_with_head "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"


print_with_head "Updating schemaFile: '$schemaFile' with metricDbSizeInGb: '$metricDbSizeInGb'"

# note: script is ran as the mongodb user.
sed -i 's/AA_REPLACE_METRICS_DB_SIZE/'"$metricDbSizeInGb"'/g' $schemaFile 

# output file
print_with_head "Loading schema"
cat $schemaFile
