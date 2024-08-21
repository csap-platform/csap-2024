#!/bin/bash

function setupEnvironment() {
	
	
	echo -e "\n\nHOME: '$HOME', \n\n Working Directory: '$(pwd)' \n\n PATH: '$PATH' \n\n release: '$(uname -a 2>&1)'"

	scriptDir=$(pwd) ;

    desktopEnvFolder="$HOME/git/wcsap/csap-packages/csap-package-linux/environment" ;
	
	csapEnvFile="not-found" ;

	if [ -e installer/csap-environment.sh ] ; then
		csapEnvFile="installer/csap-environment.sh" ;
	
	elif [ -e ../environment/csap-environment.sh ] ; then
	
		cd ..
		scriptDir=$(pwd) ;
		ENV_FUNCTIONS=$scriptDir/environment/functions ;
		csapEnvFile="$scriptDir/environment/csap-environment.sh" ;

	
	elif test -d $desktopEnvFolder ; then
	
		scriptDir="$desktopEnvFolder/../installer" ;
        ENV_FUNCTIONS="$desktopEnvFolder/functions" ;
		csapEnvFile="$desktopEnvFolder/csap-environment.sh" ;

        jmeterRunDir="$HOME/git/wcsap/test/csap-traffic-test/docker-performance/target"
		
	else
	
		csapEnvFile="$scriptDir/platform-bin/csap-environment.sh"
		
	fi
	
	echo -e "\n\n ====> Loading $csapEnvFile \n\n" ;
	source $csapEnvFile ;
	print_section "Environment loaded: $csapEnvFile" ;
	
	
	
	
	if test -d /usr/local/opt/coreutils/libexec/gnubin ; then 
	
		#
		# Handle mac os https://ryanparman.com/posts/2019/using-gnu-command-line-tools-in-macos-instead-of-freebsd-tools/
		#
		export GNUBINS="$(find /usr/local/opt -type d -follow -name gnubin -print)";

		for bindir in ${GNUBINS[@]}; do
		  export PATH=$bindir:$PATH;
		done;
	
		print_command \
			"added gnubin to front of PATH" \
			"$(echo $PATH | tr ':' '\n' )"
			# "$(echo $PATH | tr ':' '\n'; echo -e '\n\n cp info:\n ';which cp ; cp --version; )"
	#	exit
	fi ;


	print_two_columns "scriptDir" "$scriptDir" ;
	
}

setupEnvironment ;

#print_command "ls -al $(pwd)" "$(ls -al)"

export JMETER_HOME="$HOME/csap/apache-jmeter-5.5"

if [ -z "$jmeterRunDir" ] \
    || ! test -d "$(dirname jmeterRunDir)" ; then
    print_error "jmeterRunDir '$jmeterRunDir'  does not exit - exiting" ;
    exit 99 ;
fi ;

if  ! test -d $JMETER_HOME  ; then
    print_error "JMETER_HOME '$JMETER_HOME' does not exist"
    exit 99 ;
fi ;


if  test -d "$jmeterRunDir" ; then
    # --verbose
    print_two_columns "clean up" "$(rm --force --recursive $jmeterRunDir 2>&1)" ;
fi ;

print_two_columns "creating run folder" "$(mkdir --parent --verbose $jmeterRunDir 2>&1)" ;

cd $jmeterRunDir

testDefinitionFile="$(dirname $jmeterRunDir)/test-default.jmx" ;
print_two_columns "testDefinitionFile" "$testDefinitionFile" ;

#
# build user.properties
#
userTemplateProperties="$(dirname $jmeterRunDir)/user-template.properties" ;
userProperties="$jmeterRunDir/user.properties" ;

print_separator "building $userProperties" ;
cp --verbose $userTemplateProperties $userProperties

runMinutes=${runMinutes:-1};
rampSeconds=${rampSeconds:-10};

queryThreads=${queryThreads:-6};
queryDelayMs=${queryDelayMs:-0};

purgeDbThreads=${purgeDbThreads:-7};
purgeDelayMs=${purgeDelayMs:-100};

burnThreads=${burnThreads:-0};
burnDelayMs=${burnDelayMs:-1000};


print_two_columns "runMinutes" "'$runMinutes'"
print_two_columns "rampSeconds" "'$rampSeconds'"

print_two_columns "queryThreads" "'$queryThreads'"
print_two_columns "queryDelayMs" "'$queryDelayMs'"

print_two_columns "purgeDbThreads" "'$purgeDbThreads'"
print_two_columns "purgeDelayMs" "'$purgeDelayMs'"

print_two_columns "burnThreads" "'$burnThreads'"
print_two_columns "burnDelayMs" "'$burnDelayMs'"

print_two_columns "jmeterFolder" "'$jmeterFolder'"
print_two_columns "jmeterDistFile" "'$jmeterDistFile'"
print_two_columns "localDistFolder" "'$localDistFolder'"


replace_all_in_file '_TEST_DURATION_SECONDS_' "$(($runMinutes*60)) " $userProperties ;
replace_all_in_file '_RAMP_SECONDS_' $rampSeconds ;

replace_all_in_file '_QUERY_THREADS_' $queryThreads ;
replace_all_in_file '_QUERY_DELAY_MS_' $queryDelayMs ;

replace_all_in_file '_BURN_THREADS_' $burnThreads ;
replace_all_in_file '_BURN_DELAY_MS_' $burnDelayMs ;

replace_all_in_file '_PURGE_DB_THREADS_' $purgeDbThreads ;
replace_all_in_file '_PURGE_DELAY_MS_' $purgeDelayMs ;


# replace_all_in_file '_TEST_DURATION_SECONDS_' "$(($runMinutes*60)) " $userProperties ;
# replace_all_in_file '_RAMP_SECONDS_' $rampSeconds ;

# replace_all_in_file '_QUERY_DELAY_MS_' $queryDelayMs ;
# replace_all_in_file '_QUERY_THREADS_' $queryThreads ;

# replace_all_in_file '_BURN_THREADS_' $burnThreads ;



#
#  build parameters
#
runUi=true ;
params="--addprop $userProperties --logfile $jmeterRunDir --testfile $testDefinitionFile" ;
export JVM_ARGS="-Xms2048m -Xmx2048m"

if $runUi ; then

    print_section "launching ui: $params"
    # $JMETER_HOME/bin/jmeter.sh $params &
	java -DsocksProxyHost=localhost -DsocksProxyPort=8302 -jar $JMETER_HOME/bin/ApacheJMeter.jar  $params &

else
    print_section "launching test: $params"
    $JMETER_HOME/bin/jmeter.sh --nongui $params

    print_two_columns "creating report folder" "$(mkdir --parent --verbose $jmeterRunDir/report 2>&1)" ;
    reportParams="--reportonly $jmeterRunDir/jmeter-test-results.jtl --addprop $userProperties";
    reportParams="$reportParams --reportoutputfolder $jmeterRunDir/report"
    print_section "Building Report: $reportParams"
    $JMETER_HOME/bin/jmeter.sh $reportParams
fi ;



exit ;


#    _    ____   _    ____ _   _ _____       _ __  __ _____ _____ _____ ____
#    / \  |  _ \ / \  / ___| | | | ____|     | |  \/  | ____|_   _| ____|  _ \
#   / _ \ | |_) / _ \| |   | |_| |  _|    _  | | |\/| |  _|   | | |  _| | |_) |
#  / ___ \|  __/ ___ \ |___|  _  | |___  | |_| | |  | | |___  | | | |___|  _ <
# /_/   \_\_| /_/   \_\____|_| |_|_____|  \___/|_|  |_|_____| |_| |_____|_| \_\ 5.5

# Copyright (c) 1999-2022 The Apache Software Foundation

# 	--?
# 		print command line options and exit
# 	-h, --help
# 		print usage information and exit
# 	-v, --version
# 		print the version information and exit
# 	-p, --propfile <argument>
# 		the jmeter property file to use
# 	-q, --addprop <argument>
# 		additional JMeter property file(s)
# 	-t, --testfile <argument>
# 		the jmeter test(.jmx) file to run. "-t LAST" will load last 
# 		used file
# 	-l, --logfile <argument>
# 		the file to log samples to
# 	-i, --jmeterlogconf <argument>
# 		jmeter logging configuration file (log4j2.xml)
# 	-j, --jmeterlogfile <argument>
# 		jmeter run log file (jmeter.log)
# 	-n, --nongui
# 		run JMeter in nongui mode
# 	-s, --server
# 		run the JMeter server
# 	-E, --proxyScheme <argument>
# 		Set a proxy scheme to use for the proxy server
# 	-H, --proxyHost <argument>
# 		Set a proxy server for JMeter to use
# 	-P, --proxyPort <argument>
# 		Set proxy server port for JMeter to use
# 	-N, --nonProxyHosts <argument>
# 		Set nonproxy host list (e.g. *.apache.org|localhost)
# 	-u, --username <argument>
# 		Set username for proxy server that JMeter is to use
# 	-a, --password <argument>
# 		Set password for proxy server that JMeter is to use
# 	-J, --jmeterproperty <argument>=<value>
# 		Define additional JMeter properties
# 	-G, --globalproperty <argument>=<value>
# 		Define Global properties (sent to servers)
# 		e.g. -Gport=123
# 		 or -Gglobal.properties
# 	-D, --systemproperty <argument>=<value>
# 		Define additional system properties
# 	-S, --systemPropertyFile <argument>
# 		additional system property file(s)
# 	-f, --forceDeleteResultFile
# 		force delete existing results files and web report folder if
# 		 present before starting the test
# 	-L, --loglevel <argument>=<value>
# 		[category=]level e.g. jorphan=INFO, jmeter.util=DEBUG or com
# 		.example.foo=WARN
# 	-r, --runremote
# 		Start remote servers (as defined in remote_hosts)
# 	-R, --remotestart <argument>
# 		Start these remote servers (overrides remote_hosts)
# 	-d, --homedir <argument>
# 		the jmeter home directory to use
# 	-X, --remoteexit
# 		Exit the remote servers at end of test (non-GUI)
# 	-g, --reportonly <argument>
# 		generate report dashboard only, from a test results file
# 	-e, --reportatendofloadtests
# 		generate report dashboard after load test
# 	-o, --reportoutputfolder <argument>
# 		output folder for report dashboard

