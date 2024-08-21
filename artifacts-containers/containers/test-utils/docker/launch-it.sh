#!/bin/bash

function print_two_columns() {
	printf "%25s: %-20s\n" "$@";
}

function print_section() {
	echo -e  "\n* \n**\n*** \n**** \n*****  $* \n**** \n*** \n** \n*"
}

print_section "container settings"
print_two_columns "os.date" "$(date +"%h-%d-%I-%M-%S")" ;
print_two_columns "os.host" "$(hostname --long)"
print_two_columns "os.arch" "$(lscpu | grep -i architecture | tr --squeeze-repeats ' ' | cut --delimiter=' ' --fields='2' )"
print_two_columns "os.user" "$(id)"
print_two_columns "os.path" "$PATH"
print_two_columns "os.version" "$(source /etc/os-release; echo $ID-$VERSION_ID)"
print_two_columns "java.version" "$(java --version)"
print_two_columns "location" "$location: docker volume mount desired location"
print_two_columns "--note" "specify  docker volume mount desired location to container folder '/csap-data'"
print_two_columns "parameters" "$parameters"
print_two_columns "--note" "specify  docker environment variable 'parameters' to customize"


#print_section "Starting fio: --directory=$testFolder $params"
theCommand="java $parameters -jar $testFolder/*.jar"

if [[ $parameters == "transfer" ]] ; then
  theCommand="cp --verbose --recursive $dataFolder/$testFolder /transfer"
fi ;
print_section "running  $theCommand"
echo "___STARTING_RUN___"
eval $theCommand

