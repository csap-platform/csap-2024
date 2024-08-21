#!/bin/bash

function print_two_columns() {
	printf "%25s: %-20s\n" "$@";
}

function print_section() {
	echo -e  "\n* \n**\n*** \n**** \n*****  $* \n**** \n*** \n** \n*"
}


print_two_columns "os.date" "$(date +"%h-%d-%I-%M-%S")" ;
print_two_columns "os.host" "$(hostname --long)"
print_two_columns "os.user" "$(id)"
print_two_columns "os.path" "$PATH"
print_two_columns "os.version" "$(cat /etc/redhat-release)"
print_two_columns "stress-ng.version" "$(stress-ng --version)"
print_two_columns "note" "set parameters env variable. eg --cpu 4"


print_section "Starting stress-ng: $parameters"

stress-ng $parameters
