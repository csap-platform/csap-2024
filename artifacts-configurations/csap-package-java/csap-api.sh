#!/bin/bash

#
#  NOTE WHEN UPDATING: version is ALSO in csap-package-java-secondary/pom.xml for host installation
#

#jdkDistFile=${jdkDistFile:-openjdk-17.0.2_linux-x64_bin.tar.gz} ;
#jdkDistFile=${jdkDistFile:-OpenJDK17U-jdk_x64_linux_hotspot_17.0.4.1_1.tar.gz} ;
jdkDistFile=${jdkDistFile:-zulu17.40.19-ca-jdk17.0.6-linux_x64.tar.gz} ;


localDistFolder=${localDistFolder:-/perfshared/csap} ;

isZingJdk=false ;
if [[ "$jdkDistFile" =~  zing ]] ; then isZingJdk=true ; fi


isZuluJdk=false ;
if [[ "$jdkDistFile" =~  zulu ]] ; then isZuluJdk=true ; fi

isZulu8Jdk=false ;
if [[ "$jdkDistFile" =~  zulu8 ]] ; then isZulu8Jdk=true ; fi

# strip off for a shorter name for folder
#versionLessSuffix=${jdkDistFile%_linux-x64_bin.tar.gz} ;
#versionLessPrefix=${versionLessSuffix#*openjdk-} ;
versionLessSuffix=${jdkDistFile%.1_1.tar.gz} ;
versionLessPrefix=${versionLessSuffix#*hotspot_} ;

if $isZingJdk || $isZuluJdk ; then
	# zulu17.36.13-ca-jdk17.0.4-linux_x64.tar.gz
	# zing22.02.202.0-1-jdk17.0.3-linux_x64.tar.gz
	versionLessSuffix=${jdkDistFile%-linux_x64.tar.gz} ;
	versionLessPrefix=${versionLessSuffix#*-jdk} ;
fi ;


shortVersion=${shortVersion:-$versionLessPrefix}

isArmJdkNeeded=false;
#
# amazon arm is convenient, but optionally switch to 
#
amazonJdkUrl="https://corretto.aws/downloads/latest/amazon-corretto-17-aarch64-linux-jdk.tar.gz"
if [[ "$( printOsArchitecture )" =~  aarch64 ]] ; then
#  && [[ "$(printOsName)" =~  amazon ]]  ; then
  isArmJdkNeeded=true ;
  shortVersion="amz-corretto-17"
fi


print_separator "CSAP Java Package - multiple versions supported"
 
print_two_columns "jdkDistFile" "'$jdkDistFile'"
print_two_columns "os" "os '$( printOsName )' - arch '$( printOsArchitecture )'"
print_two_columns "isArmJdkNeeded" "'$isArmJdkNeeded'"
print_two_columns "isZingJdk" "'$isZingJdk'"
print_two_columns "isZuluJdk" "'$isZuluJdk'"
print_two_columns "isZulu8Jdk" "'$isZulu8Jdk'"
print_two_columns "localDistFolder" "'$localDistFolder'"
print_two_columns "version" "'$shortVersion'"



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

	print_section "api_package_get: Updating $csapPackageDependencies"

  rm --recursive --force --verbose $csapPackageDependencies
	
	mkdir --parents --verbose $csapPackageDependencies
	cd $csapPackageDependencies
	
	# support local vms
	if test -f $localDistFolder/$jdkDistFile ; then
		print_line using local copies from $localDistFolder
		cp --verbose $localDistFolder/$jdkDistFile . ; exit_on_failure $?
		
	else		
		
		#print_line "Downloading from toolsServer: http://$toolsServer/java"
		#wget -nv "http://$toolsServer/java/$jdkDistFile"
		
#		csap_mvn dependency:copy -Dtransitive=false -Dartifact=bin:jdk:$shortVersion:tar.gz -DoutputDirectory=$(pwd)
		csap_mvn dependency:copy -Dtransitive=false -Dartifact=org.csap.bin:java:$shortVersion:tar.gz -DoutputDirectory=$(pwd)

		exit_on_failure $?

	fi ;
	
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
# startWrapper should always check if $csapWorkingDir exists, if not then create it using $csapPackageDependencies
# 
#
function api_service_start() {
	
	
	
	#
	# We add serviceName to params so that process state is reflected in UI

	versionFolder="$csapWorkingDir/version/$shortVersion" ;
	
	print_two_columns "creating" "$versionFolder"
	mkdir --parents --verbose $versionFolder
	touch "$versionFolder/empty.txt" 

	install_java ;

	cd $csapWorkingDir ;

}

function install_java() {
	
	local javaFolderName="openjdk-$shortVersion" ;

	if $isZingJdk ; then
		javaFolderName="zing-$shortVersion" ;
	fi ;
	if $isZuluJdk ; then
		javaFolderName="zulu-$shortVersion" ;
	fi ;

	if $isArmJdkNeeded ; then
		javaFolderName="amazon-$shortVersion" ;
	fi ;
	
	print_two_columns "csapPackageDependencies" "$csapPackageDependencies" ;
	print_two_columns "source" "$csapWorkingDir" ;
	print_two_columns "javaFolderName" "$javaFolderName" ;
	
	local javaVersionsDir="$(dirname $CSAP_FOLDER)/java" ;
	local installPath="$javaVersionsDir/$javaFolderName" ;
	
	local testOnly=false;
	
	if [ -d  $installPath ] ; then
	
		testOnly=true;
		
		installPath="$installPath"-test ;
		print_with_head "found existing '$installPath', testonly install in $installPath"
		chmod --recursive 755 $installPath 
		\rm --recursive --force $installPath
		
	fi
	
	
	print_two_columns "installPath" "$installPath" ;
	
	mkdir --parents --verbose $javaVersionsDir ;
	print_two_columns "java base" "$javaVersionsDir"
	cd $javaVersionsDir
	
	
	local csapEnvFile=$HOME/.csapEnvironment ;

	local isDefaultJava=$(if [[ $(basename "$csapWorkingDir") == "csap-package-java" ]]; then echo true; else echo false; fi) ;

	if $testOnly ; then
		print_two_columns "test run" "skipping update of $csapEnvFile"


	elif $isZingJdk ; then
	
		ZING17_HOME=$installPath
		
		print_two_columns "deleting" "ZING17_HOME from $csapEnvFile"
		delete_all_in_file "ZING17_HOME" $csapEnvFile ; 
		
		append_line  export ZING17_HOME=$installPath ;
		
	elif $isZuluJdk ; then

	  if $isZulu8Jdk ; then

      ZULU8_HOME=$installPath
      print_two_columns "deleting" "ZULU8_HOME from $csapEnvFile"
      delete_all_in_file "ZULU8_HOME" $csapEnvFile ;
      append_line  export ZULU8_HOME=$installPath ;

	  else

      ZULU17_HOME=$installPath
      print_two_columns "deleting" "ZULU17_HOME from $csapEnvFile"
      delete_all_in_file "ZULU17_HOME" $csapEnvFile ;
      append_line  export ZULU17_HOME=$installPath ;

      # when ZULU is default JAVA 17 implementation
      if $isDefaultJava ; then
          print_two_columns "deleting" "JAVA17_HOME from $csapEnvFile"
          delete_all_in_file "JAVA17_HOME" $csapEnvFile ;
          append_line  export JAVA17_HOME=$installPath
      fi;
	  fi
		
	else
	
		JAVA17_HOME=$installPath
		
		print_two_columns "deleting" "JAVA17_HOME from $csapEnvFile"
		delete_all_in_file "JAVA17_HOME" $csapEnvFile ;
		append_line  export JAVA17_HOME=$installPath
	fi ;

  if $isDefaultJava ; then
    print_two_columns "default java" "detected because $csapWorkingDir matches csap-package-java"
    delete_all_in_file "JAVA_HOME"  ;

    append_line  export JAVA_HOME=$installPath ;
    append_line  export PATH=\$JAVA_HOME/bin:\$PATH ;

  else
    print_two_columns "Not default" "detected because csapWorkingDir does not matche csap-package-java"
  fi ;

	if ! test -d $csapPackageDependencies ; then
		api_package_get ;
	fi ;
	
	local numJdkFiles=$(ls $csapPackageDependencies/*jdk*.tar.gz | wc -l) ;

	if (( $numJdkFiles ==  0)) ; then
		print_error "Failed to locate jdk.tar.gz in $csapPackageDependencies" 
		exit 99 ;
	fi ;
	
	\rm --recursive --force temp
	mkdir --parents --verbose temp
	cd temp
	
	print_two_columns "extracting"  "$csapPackageDependencies/*jdk*.tar.gz"
	print_two_columns "destination"  "$(pwd)"

	if $isArmJdkNeeded ; then

	  print_separator "amazon arm detected"
	  print_two_columns "removing previous" "$(rm --verbose $csapPackageDependencies/*.tar.gz)"
	  print_two_columns "downloading latest" "$amazonJdkUrl"
	  wget --no-verbose --content-disposition $amazonJdkUrl
	  print_two_columns "moving" "$(mv --verbose *.tar.gz $csapPackageDependencies/)"

	fi ;

  tar --preserve-permissions --extract --gzip --file $csapPackageDependencies/*.tar.gz
  print_two_columns "moving" "$(pwd)/* to $installPath" ;
  mv --force * $installPath
	
	print_two_columns "permissions" "running chmod --recursive 555 $installPath" ;
	chmod --recursive 555 $installPath 
	
	
}
