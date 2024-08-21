#!/usr/bin/env bash

#
# Manage runtime settings on mac os, windows, cygwwin  etc
#

scriptDir=$(pwd)
scriptName=$(basename $0) ;

# mavenParameters="clean package" ;
#mavenParameters="-Dtest=Pending* -DfailIfNoTests=fals clean package" ;
mavenParameters="-Dmaven.test.skip=true clean package install" ;

gradleParameters="clean build"
gradleParameters="build -x test"

csapHome="$HOME/csap";

originalBuildZip="$csapHome/csap-host-22.03.zip"
thirdPartyDistributions="$csapHome/thirdPartyDist"

NOW=$(date +"%m-%d-%I-%M") ; # date +"%h-%d-%I-%M"
latestBuildFolder="$csapHome/build"
latestBuildZip="$latestBuildFolder/csap-host-$NOW.zip"

#
# load csap helper scripts and setup variables
#
function setupEnvironment() {


	echo -e "\n\nHOME: '$HOME', \n\n Working Directory: '$(pwd)' \n\n PATH: '$PATH' \n\n release: '$(uname -a 2>&1)'"

	csapEnvFile="not-found" ;
	if [ -e installer/csap-environment.sh ] ; then
		csapEnvFile="installer/csap-environment.sh" ;

	elif [ -e ../environment/csap-environment.sh ] ; then

		cd ..
		scriptDir=$(pwd) ;
		ENV_FUNCTIONS=$scriptDir/environment/functions ;
		csapEnvFile="$scriptDir/environment/csap-environment.sh" ;

	else

		csapEnvFile="$scriptDir/platform-bin/csap-environment.sh"

	fi

	echo "Loading $csapEnvFile" ;
	source $csapEnvFile ;
	print_section "Loaded complete: $csapEnvFile" ;

	print_command \
	  "Active Shell Command" \
	  "$(ps -p $$)"

	if [[ "$(ps -p $$)" !=  *"bash"* ]] ; then
	    print_error "bash not being used - verify setup" ;
	    delay_with_message 10 "run likely to fail"
  fi ;


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

	cd $scriptDir/../.. ;
	buildDir=$(pwd) ;
	cd $scriptDir


	print_two_columns "csapHome" "$csapHome" ;
  if ! test -d "$csapHome" ; then
    print_error "Failed to find  csapHome '$csapHome'. Folder is required for local builds"
    exit 99 ;
  fi ;


	print_two_columns "buildDir" "$buildDir" ;
  if ! test -d $buildDir ; then
    print_error "Failed to find  buildDir '$buildDir'. Folder is required for local builds"
    exit 99 ;
  fi ;
}