#!/bin/bash
#
#
#

scriptDir=`dirname $0`

source $CSAP_FOLDER/bin/csap-environment.sh

if [ "$csapName" == "$csapAgentName" ] ; then
	print_line "$csapAgentName stop not supported. Use a kill which will autorestart the service"
	exit ;
fi

export JAVA_OPTS=""
cd $csapWorkingDir

if [ "$csapServer" == "SpringBoot" ] ; then
	print_if_debug  "stopInstance.sh\t:" == Spring Boot...
	source csap-integration-springboot.sh
	stopBoot

elif [ "$csapServer" == "csap-api" ] ; then
	
	source csap-integration-api.sh
	
	print_if_debug "invoking stopWrapper"
	
	if `is_function_available api_service_stop` ; then
		api_service_stop
	else
		stopWrapper
	fi
	
	
	print_line "Token to exit read loop in OsCommandRunner.java -csap-abort-script-io-" ;
	print_line "exiting"
	exit ;

elif [ "$csapTomcat" == "true" ] ; then 
	
	if [ -e $tomcat_wrapper ]  ; then
		source $tomcat_wrapper
		tomcatStop
		print_line "tomcat stop has completed. Use csap application portal and logs to verify service is active."
		exit ;
	else
		print_line "Did not find $tomcat_wrapper. Update your application to include it."
	fi;

else
	print_error "Unhandled csapServer: $csapServer . Contact your Application manager for support"
fi;


asyncProfilerAttachFile="/tmp/.java_pid${csapPids}";
if test -f  $asyncProfilerAttachFile ; then
	print_section "async-profiler attach file detected: $asyncProfilerAttachFile"
	print_command "Cleaning up  $asyncProfilerAttachFile" "$(run_using_root rm --verbose  $asyncProfilerAttachFile)"
fi ;


