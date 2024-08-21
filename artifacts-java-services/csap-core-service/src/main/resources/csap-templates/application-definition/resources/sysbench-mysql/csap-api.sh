#!/bin/bash



print_section "CSAP API: $csapName csapJob: '$csapJob'"

# print_command "environment variables" "$(env)"



function do_configuration() {

  print_two_columns "db_host" "$db_host"
  
  #
  # Force a "short" run for demos and testing
  #

	export testProfile=${testProfile:-default} ;
  print_two_columns "testProfile" "$testProfile"
  
	export minutesToRun=${minutesToRun:-1} ;
  print_two_columns "minutesToRun" "$minutesToRun"
  
	export reportInterval=${reportInterval:-5} ;
  print_two_columns "reportInterval" "$reportInterval seconds"
 

}

do_configuration

#
# Use this for any software "build" operations. Typically used for building c/c++ code
# -  eg. apache httpd, redis
# -  This method should also upload into a repository (usually maven)
# - Note: most software packages are available as prebuilt distributions, in which case no implementation is required
#
function api_package_build() { print_if_debug "api_package_build not used" ; }

#
# Use this for getting binary packages - either prebuilt by distributions (tomcat, mongodb, cassandra,etc.)
#   or built using buildAdditionalPackages() above
#   Note that CSAP deploy will invoke this on the primary host selected during deployment, and then automatically
#   - synchronize the csapPackageDependencies to all the other hosts (via local network copy) for much faster distribution
#
function api_package_get() {
  print_if_debug "api_package_build not used" ;
}

#
# CSAP agent will always kill -9 after this command
#
#
function api_service_kill() { print_if_debug "api_service_kill not used" ; }

#
# CSAP agent will always kill -9 after this command. For data sources - it is recommended to use the
# shutdown command provided by the stack to ensure caches, etc. are flushed to disk.
#
function api_service_stop() { print_if_debug "STOP not used" ; }


#
# Note: commands should be launched in the background
#
function api_service_start() {

	
  if ! test -d "$csapLogDir" ; then
  		print_two_columns "creating logs" "in $csapLogDir, and linking /var/log/messages"
  		mkdir --parents $csapLogDir
	fi ;
	
	local testScript="$csapResourceFolder/common/db-perf-tester.sh"
	chmod 755 $testScript
	launch_background "$testScript" "" "$csapLogDir/_$csapName-output.log"
	
	print_two_columns "Note" "Test will end automatically: creating csap stop file to prevent alerts: '$csapStopFile'";
  touch $csapStopFile
}




