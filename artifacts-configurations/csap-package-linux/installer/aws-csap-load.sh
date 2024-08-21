#!/bin/bash



function print() {
	echo -e "\n\n\n---------------   $*  ------------------" ;
}


function print_section() {
	echo -e  "\n* \n**\n*** \n**** \n*****  $* \n**** \n*** \n** \n*"
}


print_section "Starting CSAP aws-csap-load.sh installer as $(id)"


print "Installing required packages"
yum --assumeyes install  unzip wget


definitionProvider="ec2-11-22-33-44.us-west-2.compute.amazonaws.com" ;
csapInstallerUrl="http://$definitionProvider:8011/api/agent/installer"

print "Getting $csapInstallerUrl"
wget --no-verbose --content-disposition $csapInstallerUrl 2>&1
definition_return_code="$?" ;

if (( $definition_return_code != 0 )) ; then
  print_section "Failed to retrieve definition - verify definitionProvider: $definitionProvider"
  exit $definition_return_code ;
fi ;

print "Extracting installer"
unzip -j csap-host-*.zip csap-platform/packages/csap-package-linux.zip && unzip -qq csap-package-linux.zip installer/*

print "Starting Installer"
installer/install.sh -noPrompt -ignorePreflight \
    -runCleanUp -deleteContainers \
    -installCsap "default" \
    -csapDefinition default