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
print_two_columns "os.version" "$(cat /etc/redhat-release)"
print_two_columns "fio.version" "$(fio -version)"
print_two_columns "location" "$location: docker volume mount desired location"
print_two_columns "--note" "specify  docker volume mount desired location to container folder '/csap-data'"
print_two_columns "parameters" "$parameters"
print_two_columns "--note" "specify  docker environment variable 'parameters' to customize"


#print_section "Starting fio: --directory=$testFolder $params"
print_section "Starting FIO"
echo "___STARTING_RUN___"
fio --directory=$location $parameters

