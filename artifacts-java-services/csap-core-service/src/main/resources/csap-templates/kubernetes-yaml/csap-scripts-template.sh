#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh

#
#   if invoked from csap-job-run.sh will source csap-environment.sh, which includes all the helper functions and service variables
#   - ref: $CSAP_FOLDER/bin/csap-environment.sh
#   - ref: https://github.com/csap-platform/csap-core/wiki/Service-Definition#runtime-variables
#

print_section "Service Runner: $csapName"

print_command "environment variables" "$(env)"


function setup() {

	#	parameters: csap-job UI enables env variables to be customized during on demand invokation.
	#		- default variable values should always be defined, and will be used on scheduled or event based invokations
    sampleVariable="${sampleVariable:-sampleValue}";
    command="${sampleCommand:-sampleCommand}";

    print_two_columns "sampleVariable" "$sampleVariable"
    print_two_columns "sampleCommand" "$sampleCommand"

	# csapEvent will identify trigger of invokation
	# 	on-demand, event-pre-deploy event-post-deploy event-pre-start event-post-start event-pre-stop event-post-stop")
    print_two_columns "csapEvent" "$csapEvent"

}

setup ;

function sampleCommand() {
	print_section "sampleCommand"
}

function perform_operation() {

	case "$command" in
		
		"sampleCommand")
			sampleCommand
			;;
		
		 *)
	            echo "Command Not found"
	            exit 1
	esac

}

perform_operation