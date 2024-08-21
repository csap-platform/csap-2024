#!/bin/bash



print_section "CSAP API: $csapName"

# print_command "environment variables" "$(env)"



function do_configuration() {

  
  #
  # Force a "short" run for demos and testing
  #

	export timeToRunInSeconds=${timeToRunInSeconds:-10} ;
  print_two_columns "timeToRunInSeconds" "$timeToRunInSeconds"
  
	export threadIterations=${threadIterations:-1 2} ;
  print_two_columns "threadIterations" "$threadIterations"
  
	export blockSizesInKb=${blockSizesInKb:-4 8} ;
  print_two_columns "blockSizesInKb" "$blockSizesInKb"
 

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
	
	local testScript="$csapResourceFolder/common/fio-tester.sh"
	chmod 755 $testScript
	launch_background "$testScript" "" "$csapLogDir/_$csapName-output.log"
	
	print_two_columns "Note" "Test will end automatically: creating csap stop file to prevent alerts: '$csapStopFile'";
  touch $csapStopFile
}




