# uptime and df, Summary of host availability

runLocation="_file_"



#
# set report to run to invoke
#

jvmProcessIdentifier="_serviceName_"                                           # unique ps args output, eg wd.tag, servicename, etc
processOwnerUser="csap"                                                        # java jcmd must be the same uid
report_folder="$csapPlatformWorking/$jvmProcessIdentifier/logs/reports"        # report folder. default is working dir $WDHOME/_profiling



mission_control_seconds=300

# native_memory_report=run
# native_memory_diff_seconds=$(( 7*60 ))


#
#  java mission control:   https://www.oracle.com/java/technologies/jdk-mission-control.html
#     - requires: settings file
#
#  native memory tracking: https://docs.azul.com/prime/NMT
#     - requires: -XX:NativeMemoryTracking=detail
#




retry_attempts=60
function run_setup() {
  
  for (( iteration=$retry_attempts; iteration > 0; iteration--)) ; do
  
    processDetails="$(ps -ef | grep $jvmProcessIdentifier | grep -v grep)"
    
    if [[ "$processDetails" == "" ]] ; then
      delay_with_message 2 "waiting for process $jvmProcessIdentifier"
    else
      break ; 
    fi
  
  done ;
  
  
  if [[ "$processDetails" == "" ]] ; then
    print_error "Failed to find pid"
    exit 99 ;
  fi ;

  #print_command "processDetails" "$(echo $processDetails)"

  processOwnerUser=$(echo "$processDetails" | awk '{print $1}')
  
  jvmProcessId=$(echo "$processDetails" | awk '{print $2}') 
  
  # javaHome="/usr/local/zingjdk_17-23_02_200"
  javaPath=$(echo "$processDetails" | awk '{print $8}')
  javaHome=$( dirname $( dirname $javaPath ) ) 
  reportPostfix="-zing-17"
  if [[ "$javaHome" =~ zingjdk_8 ]] ; then
    # javaHome="/usr/local/zingjdk_8_21_08_502" 
    reportPostfix="-zing-8"
  elif [[ "$javaHome" =~ zulu-8 ]] ; then
        # javaHome="/usr/local/zingjdk_8_21_08_502"
        reportPostfix="-zulu-8"
  elif [[ "$javaHome" =~ zulu-17 ]] ; then
        # javaHome="/usr/local/zingjdk_8_21_08_502"
        reportPostfix="-zulu-17"
  fi;
  

  if [[ "$jvmProcessIdentifier" == "wd.tag" ]] ; then
    report_folder="$WDHOME/_profiling" ;
  fi
  
  startTime=$(date +"%h-%d--%H-%M") ;
  reportDir=$report_folder/$startTime-$reportPostfix
  run_using_root "mkdir --parent --verbose $reportDir ; chown -R $processOwnerUser:users $report_folder; chmod -R 777 $report_folder"

}



function find_jvmProcessIdentifier() {
  
  
  
  print_command \
    "processOwnerUser: $processOwnerUser    jvmProcessId: $jvmProcessId      java: $javaHome  " \
    "$(ps -o pcpu,pid,args -p $jvmProcessId | sed 's/-D/\n\t -D/g' | sed 's/-XX/\n\t -XX/g'  )" \
    | tee --append $statusReport
    
  
}


function start_jmc() {
  
  
  local durationSeconds=${1:-300} ; 
  
  if (( $durationSeconds <= 0 )) ; then
    print_two_columns "mission control" "durationSeconds: $durationSeconds, skipping"
    return ;
  fi ;
  
  local missionProfileFile="$csapDefinitionFolder/scripts/mission-control-profile.jfc"
  local resultFile="$reportDir/java-mc-report-${startTime}-${reportPostfix}.jfr"
  
  local commandOutput=$(run_using_root su - $processOwnerUser \
    -c \"$javaHome/bin/jcmd $jvmProcessId JFR.start duration=${durationSeconds}s filename=$resultFile settings=$missionProfileFile \")
    
	print_command "jcmd $jvmProcessId JFR.start duration=${durationSeconds}s settings=$missionProfileFile" \
	  "$(echo "$commandOutput" | sed "0,/${jvmProcessId}:/d")" 
	 # | tee --append $statusReport
	 
}

function show_native_memory() {
  
  local reportType=${1:-summary }
  local units=${2:-GB } # KB, MB, GB

  local commandOutput=$(run_using_root su - $processOwnerUser -c \"$javaHome/bin/jcmd $jvmProcessId VM.native_memory $reportType scale=$units \")
	print_command "jcmd $jvmProcessId VM.native_memory $reportType" \
	  "$(echo "$commandOutput" | sed "0,/${jvmProcessId}:/d")" \
	  | tee --append $statusReport
	  
}

function run_delta_reports() {
  
  local seconds=${1:-$(( 7*60 ))} ; # default first 5 minutes
  local interval=${2:-60} ;         # every 60 seconds
  local type=${3:-detail.diff } ;   # summary or detail
  local units=${4:-GB } ;           # KB, MB, GB
  
  if [[ "$javaHome" =~ zingjdk_8 ]] ; then 
    type="summary.diff"
    print_section "zing 8 detail does not work, running summary ins"
  fi ;
  
  for (( iteration=$seconds; iteration > 0;)) ; do 
  
    reportTime=$(printf "%04d" $(( $seconds -  $iteration )) )

    reportFile=$reportDir/snapshot-${reportTime}-seconds.sh;
    
    print_separator "Seconds Remaining: $iteration"
	
		# baseline
		commandOutput=$(run_using_root su - $processOwnerUser -c \"$javaHome/bin/jcmd $jvmProcessId VM.native_memory baseline\")
		
		print_two_columns "baseline" "$(echo "$commandOutput" | sed "0,/${jvmProcessId}:/d" )"
		
		print_two_columns "capturing changes" "$interval seconds"
		sleep $interval ; 
		
		# diff
		commandOutput=$(run_using_root su - $processOwnerUser -c \"$javaHome/bin/jcmd $jvmProcessId VM.native_memory $type scale=$units \")
		echo "$commandOutput" | sed "0,/${jvmProcessId}:/d" > $reportFile
		print_two_columns "Diff generated" "$reportFile"
		
		iteration=$(( $iteration - $interval ))
		
	done
  
}


run_setup

startReport=$reportDir/_start-report.sh;
endReport=$reportDir/_end-report.sh;

#
#  Run the reports
#
statusReport="$startReport"
find_jvmProcessIdentifier

if [ -n "$mission_control_seconds" ] ; then start_jmc $mission_control_seconds ; fi


if [[ "$native_memory_report" == "run" ]] ; then
  show_native_memory summary
  show_native_memory detail
fi

if [ -n "$native_memory_diff_seconds" ] ; then
  if (( $native_memory_diff_seconds > 0 )) ; then
  
    run_delta_reports $native_memory_diff_seconds
  
    # collect final status
    statusReport="$endReport"
    show_native_memory summary
    show_native_memory detail
  fi ;
fi ;







