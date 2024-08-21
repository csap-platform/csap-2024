#!/bin/bash



print_separator "$csapName Jmeter tests"


function do_configuration() {
  
  
  testToRun=${testToRun:-default};
  reportName=${reportName:-run};
  
  warmUpMinutes=${warmUpMinutes:-0};
  runMinutes=${runMinutes:-1};
  rampSeconds=${rampSeconds:-10};
  
  queryThreads=${queryThreads:-6};
  queryDelayMs=${queryDelayMs:-0};
  
  purgeDbThreads=${purgeDbThreads:-7};
  purgeDelayMs=${purgeDelayMs:-100};
  
  burnThreads=${burnThreads:-0};
  burnDelayMs=${burnDelayMs:-1000};
  
  jmeterFolder=${jmeterFolder:-$csapWorkingDir/apache-jmeter-5.5} ;
  jmeterDistFile=${jmeterDistFile:-apache-jmeter-5.5.zip} ;
  localDistFolder=${localDistFolder:-/data/csap} ;

  jmeterTestUtilsDistFile="$csapPackageFolder/test-utils/$jmeterDistFile"
  
  jmeterRunDir="$csapWorkingDir/logs/jmeterRun" ;


  print_section "reportName: $reportName"
	print_two_columns "warmUpMinutes" "'$warmUpMinutes'"
	print_two_columns "runMinutes" "'$runMinutes'"
	print_two_columns "rampSeconds" "'$rampSeconds'"
	print_two_columns "testToRun" "'$testToRun'"
	
	print_two_columns "queryThreads" "'$queryThreads'"
	print_two_columns "queryDelayMs" "'$queryDelayMs'"
	
	print_two_columns "purgeDbThreads" "'$purgeDbThreads'"
	print_two_columns "purgeDelayMs" "'$purgeDelayMs'"
	
	print_two_columns "burnThreads" "'$burnThreads'"
	print_two_columns "burnDelayMs" "'$burnDelayMs'"
	
	print_two_columns "jmeterFolder" "'$jmeterFolder'"
	print_two_columns "jmeterDistFile" "'$jmeterDistFile'"
	print_two_columns "localDistFolder" "'$localDistFolder'"
	print_two_columns "jmeterTestUtilsDistFile" "'$jmeterTestUtilsDistFile'"
	print_two_columns "jmeterRunDir" "'$jmeterRunDir'"

  install_if_needed libXrender
  install_if_needed libXtst
  install_if_needed libXi
}

do_configuration

#
#  Implement: this map directly to console ui operations
#
function api_package_build() { print_with_head "api_package_build not used" ; }

function api_package_get() {

	print_with_head "retrieving jmeter"
	
	\rm --recursive --force $csapPackageDependencies
	
	mkdir --parents --verbose $csapPackageDependencies
	cd $csapPackageDependencies
	
	# support local vms
	if test -f $localDistFolder/$jmeterDistFile ; then
		print_two_columns "local repository" "$(cp --verbose $localDistFolder/$jmeterDistFile . 2>&1)" ;

	elif test -f $jmeterTestUtilsDistFile ; then
		print_two_columns "test-utils folder" "$(cp --verbose $jmeterTestUtilsDistFile . 2>&1)" ;

	else		
		
		print_error "Remote get not implemented" ;
		exit 99 ;

	fi ;
	
	cd $csapWorkingDir ;
	
}

function api_service_kill() { 
  
  run_using_root kill -9 "$csapPids"; 
  
  if [ $isClean == "1" ] ||  [ $isSuperClean == "1"  ] ; then
    
    print_command "clean up working files" "$(rm --force --verbose --recursive $jmeterRunDir 2>&1)" ;
  
  fi ;
  
  
  
}



#
# tomcat start using detected environment
#
function api_service_stop() { 

	print_section "$csapName stop" ;

  api_service_kill

 }


#
# tomcat stop using detected environment
#
function api_service_start() {

	print_section "$csapName start" ;
	
	#
  # Optional log folder restore
  #
	if [ -e $csapWorkingDir.logs ] ; then 
		print_two_columns "restoring logs" "source: $csapWorkingDir.logs"
		mv  $csapWorkingDir.logs $csapLogDir
		print_command "$csapLogDir" "$(ls -l $csapLogDir/*)"
	else
		mkdir --parents --verbose $csapLogDir
	fi ;
	
	#
	# Copy templates 
	#
	copy_csap_service_resources

  
	if test -d $csapPackageDependencies ; then
	  print_two_columns "found" "Using existing $csapPackageDependencies"
	else
		api_package_get ;
	fi ;
	
	print_section "checking $jmeterFolder"
	if ! test -d $jmeterFolder ; then
	  print_two_columns "Extracting" "$csapPackageDependencies/*.zip"
		unzip -qq -o $csapPackageDependencies/*.zip
	fi ;
	
	
	
	
	#
	#  run folder where logs are stored
	#

  if  test -d "$jmeterRunDir" ; then
      # --verbose
      print_two_columns "clean up" "$(rm --force --verbose --recursive $jmeterRunDir 2>&1)" ;
  fi ;
  
  print_two_columns "creating run folder" "$(mkdir --parent --verbose $jmeterRunDir 2>&1)" ;
  
  local settingsTemplateFile="$csapWorkingDir/configuration/test-${testToRun}-settings.csv"
  print_two_columns "test settings" "$(cp --verbose  $settingsTemplateFile $jmeterRunDir 2>&1)" ;
  
  local settingsRunFile="$jmeterRunDir/$(basename $settingsTemplateFile)"
  replace_all_in_file '_FQDN_HOST_' $(hostname --long) $settingsRunFile ;
  
  
	#
	#
	#
	userPropertiesWarmUp="$jmeterRunDir/user-warmup-$warmUpMinutes.properties" ;
	buildJmeterProperties $userPropertiesWarmUp $warmUpMinutes;
	
	userPropertiesRun="$jmeterRunDir/user-run-$runMinutes.properties" ;
	buildJmeterProperties $userPropertiesRun $runMinutes;
	
  #
  #
  #
  
  local testDefinitionFile="$jmeterRunDir/test-${testToRun}.jmx"
  cp --verbose "$csapWorkingDir/configuration/test-${testToRun}.jmx" "$testDefinitionFile"
  
  local paramsWarmup="--addprop $userPropertiesWarmUp --logfile $jmeterRunDir --testfile $testDefinitionFile" ;
  local paramsMain="--addprop $userPropertiesRun --logfile $jmeterRunDir --testfile $testDefinitionFile" ;
  
  local jmeterRunScript=$jmeterRunDir/start-test.sh ;
  
  local jmeterStartTime=$(date +"%b-%d--%H-%M") ;
  
  mkdir --parents --verbose $csapLogDir/reports ;
  local reportFolder="$csapLogDir/reports/$reportName-$jmeterStartTime"
  print_two_columns "reportFolder" "$reportFolder" ;
  
  cat >$jmeterRunScript <<EOF
#!/bin/bash
source $CSAP_FOLDER/bin/csap-environment.sh

cd $jmeterRunDir ;

export JVM_ARGS="$JAVA_OPTS"

if (( $warmUpMinutes > 0 )) ; then
  print_section "launching $warmUpMinutes minute warmup : $(pwd) $paramsWarmup"
  
  $jmeterFolder/bin/jmeter.sh --nongui $paramsWarmup
  
  reportParams="--reportonly $jmeterRunDir/jmeter-test-results.jtl --addprop $userPropertiesWarmUp";
  reportParams="\$reportParams --reportoutputfolder $reportFolder-warmup"
  
  print_section "Building Warmup Report: \$reportParams"
  
  print_with_date "May take 10 minutes or longer for large runs"
  
  $jmeterFolder/bin/jmeter.sh \$reportParams
  
  print_two_columns "creating report folder" "\$(mkdir --parent --verbose $jmeterRunDir/warmup-results 2>&1)" ;
  print_two_columns "moving warm up files" "\$(mv --verbose $jmeterRunDir/jmeter* $jmeterRunDir/warmup-results 2>&1)" ;
  
  delay_with_message 20 "Starting main run"
fi ;

print_section "launching $runMinutes minute run: $(pwd) $paramsMain"

$jmeterFolder/bin/jmeter.sh --nongui $paramsMain


reportParams="--reportonly $jmeterRunDir/jmeter-test-results.jtl --addprop $userPropertiesRun";
reportParams="\$reportParams --reportoutputfolder $reportFolder"

print_section "Building Report: \$reportParams"

print_with_date "May take 30 minutes or longer for large runs"

$jmeterFolder/bin/jmeter.sh \$reportParams

print_with_date "Report Completed"

EOF

  chmod 755 $jmeterRunScript ;
  local launchLogFile="$csapLogDir/jmeter-launch.log" ;
  local args="" ;
  launch_background "$jmeterRunScript" "$args" "$launchLogFile" 
	

	
	print_two_columns "Note" "Test will end automatically: creating csap stop file to prevent alerts: '$csapStopFile'";
  touch $csapStopFile
}

function buildJmeterProperties() {
  
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
















