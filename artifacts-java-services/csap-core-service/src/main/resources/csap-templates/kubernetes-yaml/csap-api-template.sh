#!/bin/bash



print_section "CSAP API: $csapName"

# print_command "environment variables" "$(env)"



function do_configuration() {


  testToRun=${testToRun:-default};
	print_two_columns "testToRun" "'$testToRun'"

}

do_configuration

#
# Use this for any software "build" operations. Typically used for building c/c++ code
# -  eg. apache httpd, redis
# -  This method should also upload into a repository (usually maven)
# - Note: most software packages are available as prebuilt distributions, in which case no implementation is required
#
function api_package_build() { print_with_head "api_package_build not used" ; }


#
# Use this for getting binary packages - either prebuilt by distributions (tomcat, mongodb, cassandra,etc.)
#   or built using buildAdditionalPackages() above
#   Note that CSAP deploy will invoke this on the primary host selected during deployment, and then automatically
#   - synchronize the csapPackageDependencies to all the other hosts (via local network copy) for much faster distribution
#
function api_package_get() {
  print_with_head "api_package_build not used" ;
#  print_section "api_package_get: Updating $csapPackageDependencies"
#
#  rm --recursive --force --verbose $csapPackageDependencies
#
#  mkdir --parents --verbose $csapPackageDependencies
#  cd $csapPackageDependencies
#
#  # support local vms
#  if test -f $localDistFolder/$jdkDistFile ; then
#    print_line using local copies from $localDistFolder
#    cp --verbose $localDistFolder/$jdkDistFile . ; exit_on_failure $?
#
#  else
#
#    #print_line "Downloading from toolsServer: http://$toolsServer/java"
#    #wget -nv "http://$toolsServer/java/$jdkDistFile"
#
#    csap_mvn dependency:copy -Dtransitive=false -Dartifact=bin:jdk:$shortVersion:tar.gz -DoutputDirectory=$(pwd)
#
#    exit_on_failure $?
#
#  fi ;
}





#
# CSAP agent will always kill -9 after this command
#
#
function api_service_kill() { print_with_head "api_service_kill not used" ; }

#
# CSAP agent will always kill -9 after this command. For data sources - it is recommended to use the
# shutdown command provided by the stack to ensure caches, etc. are flushed to disk.
#
function api_service_stop() { print_with_head "STOP not used" ; }


#
# Note: commands should be launched in the background
#
function api_service_start() {

  #
  # Support for restoring log files when option selected in UI
  #
  if [ -e $csapWorkingDir.logs ] ; then
    print_two_columns "restoring logs" "source: $csapWorkingDir.logs"
    mv  $csapWorkingDir.logs $csapLogDir
    print_command "$csapLogDir" "$(ls -l $csapLogDir/*)"
  else
    mkdir --parents --verbose $csapLogDir
  fi ;

  #
  # typical: .../serviceName/common/configuration contains 1 or more files used at runtime
  #   - files will be copied to the service working folder
  #   - copying files is required because application folder can be reloaded from git
  #   - multiple environments can be used by placing files in .../serviceName/ENVIRONMENT_NAME/configuration
  #
  #
	copy_csap_service_resources

  # uncomment if external dependencies existing (zips, etc)
  #
  #	if ! test -d $csapPackageDependencies ; then
  #		api_package_get ;
  #	fi ;

  # uncomment if a dependency needs to be installed into working folder
  #
  #	print_section "checking $jmeterFolder"
  #	if ! test -d $jmeterFolder ; then
  #	  print_two_columns "Extracting" "$csapPackageDependencies/*.zip"
  #		unzip -qq -o $csapPackageDependencies/*.zip
  #	fi ;



	#
	# Helper function for nohup and log management
	#
	launch_background "command" "arguments" "logfilepath"

}

function optional_buildPropertiesUsingTemplate() {

  local propertyFile=${1};
  local testMinutes=${2};
  local testSeconds=$(($testMinutes*60));


  #
  # build user.properties
  #
  local userTemplateProperties="$csapWorkingDir/configuration/user-template.properties" ;


  print_separator "building $propertyFile" ;
  cp --verbose $userTemplateProperties $propertyFile

  replace_all_in_file '_TEST_DURATION_SECONDS_' $testSeconds $propertyFile ;
  replace_all_in_file '_RAMP_SECONDS_' $rampSeconds ;

  replace_all_in_file '_QUERY_THREADS_' $queryThreads ;
  replace_all_in_file '_QUERY_DELAY_MS_' $queryDelayMs ;

  replace_all_in_file '_BURN_THREADS_' $burnThreads ;
  replace_all_in_file '_BURN_DELAY_MS_' $burnDelayMs ;

  replace_all_in_file '_PURGE_DB_THREADS_' $purgeDbThreads ;
  replace_all_in_file '_PURGE_DELAY_MS_' $purgeDelayMs ;

}
