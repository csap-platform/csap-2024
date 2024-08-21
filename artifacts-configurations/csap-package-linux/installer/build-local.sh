#!/usr/bin/env bash

#
# 
#

source ./platform-setup.sh

setupEnvironment ;


function runGradle() {

	local buildFolder=${1:-$buildDir} ;

	cd $buildFolder ;

	print_section "Starting build in $(pwd) using $gradleParameters"
	delay_with_message 2 "Starting in"

	./gradlew $gradleParameters

	exit_on_failure $? "One or more items failed to build - review and fix"


}

packageBase="csap-packages";

runGradle $buildDir



# packagesToBuild="csap-package-docker  csap-package-java  csap-package-linux csap-package-monitoring 
# csap-package-tomcat csap-package-crio csap-package-httpd csap-package-kubelet csap-package-logging  csap-package-podman" ;

declare -A buildToCsapService ;
buildToCsapService["csap-core-service.jar"]=csap-agent.jar ;
buildToCsapService["csap-starter-tester.jar"]=csap-verify-service.jar ;
buildToCsapService["csap-events.jar"]=events-service.jar ;
buildToCsapService["csap-package-docker.zip"]=docker.zip ;
buildToCsapService["csap-package-crio.zip"]=crio.zip ;
buildToCsapService[csap-package-httpd.zip]=httpd.zip ;
buildToCsapService[csap-package-kubelet.zip]=kubelet.zip ;
buildToCsapService[csap-package-podman.zip]=podman-system-service.zip ;

print_command \
	"custom sevice mappings" \
	"$(for x in "${!buildToCsapService[@]}"; do printf "[%s]=%s\n" "$x" "${buildToCsapService[$x]}" ; done)"

# 

# add the files to the original
#csapPackageZips=$(find $buildDir/$packageBase -name "*-2-SNAPSHOT.zip") ;
csapPackageZips=$(find $buildDir/$packageBase -name "*-SNAPSHOT.zip") ;

print_command "csapPackageZips" "$csapPackageZips"


csapJars=$(find $buildDir -name "*-SNAPSHOT.jar") ;
print_command "csapJars" "$csapJars"

csapWars=$(find $buildDir -name "*-SNAPSHOT.war") ;
print_command "csapWars" "$csapWars"

function setupBuildEnvironment() {

  	print_section "Creating  $latestBuildZip"

  	cd $latestBuildFolder;
  	print_two_columns "current folder" "$(pwd)"

    print_separator "cleaning up previous builds"
  	rm --force --recursive --verbose $(dirname $latestBuildZip)/*.zip csap-platform platform-bin

  #  print_separator "grabbing 3rd party distributions"
  #	cp --verbose $originalBuildZip $latestBuildZip


  	packagesFolder="csap-platform/packages"
    print_separator "creating $packagesFolder"
  	mkdir --verbose --parents $packagesFolder ;

}

function addCsapPackagesToInstaller() {

  for zipFile in $csapPackageZips ; do

		print_separator "adding csap package: $(basename $zipFile)"
		print_two_columns "folder" "$(dirname $zipFile)"

		local zipBase=$(basename $zipFile)
		print_two_columns "zipBase" "$zipBase"

    local snapZipVersion="-2-SNAPSHOT" ;
    if [[ "$zipBase" == *"22.09-SNAPSHOT"* ]] ; then
      snapZipVersion="-22.09-SNAPSHOT" ;
    fi ;
		print_two_columns "snapZipVersion" "$snapZipVersion"

    local zipMinusVersion="${zipBase%${snapZipVersion}.zip}.zip"
		local simpleZip=${zipMinusVersion#"csap-package-"}

		if [  "${buildToCsapService[$zipMinusVersion]}" != "" ] ; then
			simpleZip=${buildToCsapService[$zipMinusVersion]};
			print_two_columns "customMapping" "$simpleZip"

      cp --verbose $zipFile $packagesFolder/$simpleZip
#      zip $latestBuildZip $packagesFolder/$simpleZip

    else

      cp --verbose $zipFile $packagesFolder/$zipMinusVersion
#      zip $latestBuildZip $packagesFolder/$zipMinusVersion

		fi ;

	done ;

	print_separator "adding csap package dependencies: $thirdPartyDistributions"
	cp --recursive --verbose $thirdPartyDistributions/* $packagesFolder


}

function addJarsToInstaller() {

	for jarFile in $csapJars ; do

		print_separator "adding csap java service: $(basename $jarFile)"
		print_two_columns "folder" "$(dirname $jarFile)"


		local jarBase=$(basename $jarFile)
		print_two_columns "jarBase" "$jarBase"

		local snapVersion="-2-SNAPSHOT" ;
		if [[ "$jarBase" == *"22.09-SNAPSHOT"* ]] ; then
		  snapVersion="-22.09-SNAPSHOT" ;
    fi ;

		local jarMinusVersion="${jarBase%${snapVersion}.jar}.jar"

		print_two_columns "jarMinusVersion" "$jarMinusVersion"

		local deployJarName="${buildToCsapService[$jarMinusVersion]}" ;


		if [  "$deployJarName" != "" ] ; then

			print_two_columns "including" "$jarMinusVersion"

			cp --verbose $jarFile $packagesFolder/$deployJarName
#			zip $latestBuildZip $packagesFolder/$deployJarName


		else
			print_two_columns "ignoring" "$jarMinusVersion"
		fi ;

	done

  print_separator "adding csap WAR files to $packagesFolder"
	for warFile in $csapWars ; do
    local warBase=$(basename $warFile)
    print_two_columns "warBase" "$warBase"

    local snapVersion="-2-SNAPSHOT" ;
    if [[ "$warBase" == *"22.09-SNAPSHOT"* ]] ; then
      snapVersion="-22.09-SNAPSHOT" ;
    fi ;

    local warMinusVersion="${warBase%${snapVersion}.war}.war"

    print_two_columns "warMinusVersion" "$warMinusVersion"

	  cp --verbose $warFile $packagesFolder/$warMinusVersion
  done
}


function addBinFiles() {

  #
  #  Initial bootstrap will overwrite this
  #
  print_separator "extracting platform-bin folder"
  unzip $packagesFolder/csap-package-linux.zip platform-bin/*

  print_separator "adding bin to $latestBuildZip"
  mv --verbose platform-bin csap-platform/bin

}

#
#  Build the csap-host.zip distribution
#
setupBuildEnvironment

addJarsToInstaller

addCsapPackagesToInstaller

addBinFiles

print_separator "adding csap-platform to : $latestBuildZip"
zip -r $latestBuildZip csap-platform


print_section "Completed build: $latestBuildZip"

