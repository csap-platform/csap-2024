#!/bin/bash

source $CSAP_FOLDER/bin/csap-environment.sh

#
#   References:
#   - docker options: https://hub.docker.com/r/severalnines/sysbench/
#   - sysbench scenarios: https://github.com/centminmod/centminmod-sysbench
#
#   Invokation NOtes
#   if invoked from csap-job-run.sh will source csap-environment.sh, which includes all the helper functions and service variables
#   - ref: $CSAP_FOLDER/bin/csap-environment.sh
#   - ref: https://github.com/csap-platform/csap-core/wiki/Service-Definition#runtime-variables
#

print_section "Service Runner: $csapName"



function setup() {

  
  # print_command "environment variables" "$(env)"
  
  
  print_two_columns "db_host" "$db_host"

	testProfile=${testProfile:-default} ;
  print_two_columns "testProfile" "$testProfile"
  
	minutesToRun=${minutesToRun:-60} ;
  print_two_columns "minutesToRun" "$minutesToRun"
  
	reportInterval=${reportInterval:-30} ;
  print_two_columns "reportInterval" "$reportInterval seconds"
  
  
  
	threads=${threads:-64} ;
  print_two_columns "threads" "$threads"
  
	tableCount=${tableCount:-24} ; # 24
  print_two_columns "tableCount" "$tableCount"
  
	tableSize=${tableSize:-100000} ;
  print_two_columns "tableSize" "$tableSize"
  

  sysBenchImage=${sysBenchImage:-default} ;

  if [[ "$sysBenchImage" == "default" ]] ; then
    sysBenchImage="csapplatform/sysbench:latest"
    if [[ "$(hostname --long)" == *hostx.yourcompany ]] ; then
      sysBenchImage="docker-dev-artifactory.yourcompany.com/csapplatform/sysbench" ;
    fi
  fi ;
  print_two_columns "sysBenchImage" "$sysBenchImage"

  sqlImage=${sqlImage:-default} ;
  print_two_columns "printOsArchitecture" "'"$( printOsArchitecture )"'"
  if [[ "$sqlImage" == "default" ]] ; then
    sqlImage="mysql:5.7" ;
    if [[ "$( printOsArchitecture )" =~ aarch64 ]] ; then
      sqlImage="arm64v8/mysql" ;
    fi
    if [[ "$(hostname --long)" == *hostx.yourcompany ]] ; then
      sqlImage=${sqlImage:-docker-public-artifactory.yourcompany.com/mysql:5.7} ;
    fi
  fi ;
  print_two_columns "sqlImage" "$sqlImage"
  
  sysBenchConnection="--mysql-host=$db_host --mysql-port=3306 --mysql-user=sbtest --mysql-password=password"
  print_two_columns "sysBenchConnection" "$sysBenchConnection"
  
  sysBenchCommand="sysbench --db-driver=mysql"
  print_two_columns "sysBenchCommand" "$sysBenchCommand"
  
  
  reportsFolder="$csapLogDir/reports" ;
  if ! test -d reportsFolder ; then
    print_two_columns "reportsFolder" "$(mkdir --verbose --parents $reportsFolder)"
  fi ;
  latestLogFile="$reportsFolder/sysbench-latest.log" ;
  print_two_columns "latestLogFile" "$latestLogFile"
  
  
  
}

setup ;



#
# creates tables for runs
#
function setup_db_for_tests() {
  
  print_command "databases" "$(sql_runner "show databases;" false| column --table)"
	
	
	print_command "Cleanup DBs" \
	  "$(sql_runner "DROP SCHEMA sbtest;")"
	 # "$(sql_runner "DROP SCHEMA sbtest; CREATE SCHEMA sbtest; GRANT ALL PRIVILEGES ON sbtest.* to sbtest@'%'")"
	
	print_command "Creating test db and user" \
	  "$(sql_runner "CREATE SCHEMA sbtest;CREATE USER sbtest@'%' IDENTIFIED BY 'password';GRANT ALL PRIVILEGES ON sbtest.* to sbtest@'%'")"
	
	# DROP SCHEMA sbtest; CREATE SCHEMA sbtest; GRANT ALL PRIVILEGES ON sbtest.* to sbtest@'%';
	# CREATE SCHEMA sbtest;CREATE USER sbtest@'%' IDENTIFIED BY 'password';GRANT ALL PRIVILEGES ON sbtest.* to sbtest@'%';
	
  
  local sysBenchPrepare="/usr/share/sysbench/tests/include/oltp_legacy/parallel_prepare.lua run" ;
	
	run_sysbench "sb-prepare" --threads=1 $sysBenchPrepare
	  

}

function sql_runner() {
  
  local sqlCommand=${1:-show databases;};
  local showErrors=${2:-true} ;
  

  if [ "$showErrors" == "true" ] ; then
    docker run --name=$csapName-Runner --rm=true --network=$db_host-network \
      $sqlImage mysql --host=$db_host --user=root --password=$db_pass --execute="$sqlCommand" \
      2>&1
  else 
    docker run --name=$csapName-Runner --rm=true --network=$db_host-network \
      $sqlImage mysql --host=$db_host --user=root --password=$db_pass --execute="$sqlCommand" \
      2>/dev/null
  fi ;
}

function run_sysbench() {
  
  local containerName="$1"
  shift 1
  local args="--oltp-table-size=$tableSize --oltp-tables-count=$tableCount $*"
  local dockerParameters="run --name=$containerName --rm=true --network=$db_host-network" ;
  dockerParameters="$dockerParameters --volume=$csapWorkingDir/csap-lua:/usr/share/sysbench/tests/include/oltp_legacy/csap-lua"
  # 
  
  print_command "docker parameters" "$(echo $dockerParameters; echo $sysBenchImage; echo $sysBenchCommand $sysBenchConnection; echo $args )"
  
  backup_file $latestLogFile
  # if test -f $latestLogFile ; then
  #   print_two_columns "latestLogFile" "$(rm --verbose --force $latestLogFile)"
  # fi ;
  
  print_separator "START: sysbench $containerName output"
  docker $dockerParameters  \
    $sysBenchImage $sysBenchCommand $sysBenchConnection \
    $args | tee $latestLogFile
    
  print_separator "COMPLETED: sysbench $containerName output"
  
}
#
# oltp scenario
#
function oltpTests() {
  
	print_section "scenario: default, running on host: $db_host"
	
  setup_db_for_tests
  
	testContainerName="oss-mysql-sysbench" ;

	local sysBenchOltp="/usr/share/sysbench/tests/include/oltp_legacy/oltp.lua run" ;
	
	run_sysbench "$testContainerName" --mysql-table-engine=innodb \
	  --report-interval=$reportInterval  --threads=$threads --time=$(( $minutesToRun * 60 )) \
	  $sysBenchOltp
}

#
# oltp scenario
#
function csapOltpCsvTests() {
  
	print_section "csapOltpCsvTests on db $db_host"
	
  setup_db_for_tests
  
	testContainerName="oss-mysql-sysbench" ;

# 	local sysBenchOltp="/usr/share/sysbench/tests/include/oltp_legacy/oltp.lua run" ;
	local sysBenchOltp="/usr/share/sysbench/tests/include/oltp_legacy/csap-lua/csap-oltp.lua run" ;
	
	run_sysbench "$testContainerName" --mysql-table-engine=innodb \
	  --report-interval=30  --threads=$threads --time=$(( $minutesToRun * 60 )) \
	  $sysBenchOltp
}

# /usr/share/sysbench/tests/include/oltp_legacy/csap-oltp.lua

function perform_operation() {

	case "$testProfile" in
		
		"default" | "oltp")
			oltpTests
			;;
			
		"csapOltp")
			csapOltpCsvTests
			;;
		
		 *)
	            echo "Command Not found"
	            exit 1
	esac

}

perform_operation