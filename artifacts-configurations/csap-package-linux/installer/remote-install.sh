#!/bin/bash


#
# mac os brew install bash coreutils wget openjdk@17 jenv
#
# set -x
#   brew install bash, on macos
#  install bash on windows: lxrun /install
#

#
#  Parameters:
#  	<host>  : default is -help, which will output setup steps. csap-01.root is vm testing
#			- NOTE: per help, ~/.ssh/config and ssh keys should be setup first.
#

#
# Sample installation 1: vmware fusion VM: set up vm then user install
#
# ./remote-install.sh csap-02.root no -noPrompt -ignorePreflight -csapVmUser peter.nightingale -runCleanUp -deleteContainers -installCsap default -csapDefinition default
# ./remote-install.sh csap-02.user no -noPrompt -ignorePreflight -runCleanUp -deleteContainers -installCsap default -csapDefinition default
#
# Sample installation 2: Install vm using default csap definition
#
#  ./remote-install.sh xxx.some.host.pattern no -noPrompt -ignorePreflight   -runCleanUp  -installCsap default -csapDefinition default


#
# Sample installation 3: Install usins template-autoplay.yaml
#
#  ./remote-install.sh some.host.pattern no -noPrompt  -ignorePreflight  -runCleanUp -autoPlaySourceFile /home/$(whoami)/csap-auto-play.yaml  -installCsap default -csapDefinition default

#
# Sample installation 4: AWS csap default installer. for kubernetes, add: -csapAutoPlay
#
#./remote-install.sh aws-01.aws no -noPrompt -ignorePreflight -runCleanUp -deleteContainers -installCsap default -csapDefinition default -updateOs
# ./remote-install.sh aws-01.aws no -autoPlaySourceFile /home/rocky/csap-auto-play.yaml -noPrompt -ignorePreflight -runCleanUp -deleteContainers -installCsap default -csapDefinition default -updateOs
#
# arm install
# ./remote-install.sh aws-02.aws no -noPrompt -ignorePreflight -runCleanUp -deleteContainers -installCsap default -csapDefinition default -updateOs
# AWS install with dcib-csap-volume second 100gb Disk nvme1n1
#  diskGb="100";./remote-install.sh aws-01.aws no -noPrompt -ignorePreflight -runCleanUp -deleteContainers -autoPlaySourceFile /home/rocky/csap-auto-play.yaml -installCsap default -csapDefinition default -updateOs -extraDisk /dev/nvme1n1 /data/dcib-csap-volume $diskGb
# Restore attempt:diskGb="-1"






# General installs: default CSAP
#

# Example1: uninstall
# csap-01.root - -noPrompt -ignorePreflight -uninstall
#
#
# Example 2: default definition with os updates and installHome /opt
# csap-01.root - -noPrompt -updateOs -ignorePreflight -runCleanUp -deleteContainers  -installHome /opt  -installCsap default -csapDefinition default
#
#
# Example 3: default definition, all-in-one csapAutoPlay, skip kernel and host transfer file 
# ./remote-install.sh csap-01.root - -noPrompt -ignorePreflight -csapAutoPlay -runCleanUp -deleteContainers  -installHome /opt  -installCsap default -csapDefinition default
# 
#
# Example 4: default definition, custom os repos
# csap-01.root - -noPrompt -ignorePreflight -overwriteOsRepos -runCleanUp -deleteContainers  -installCsap default -csapDefinition default
# 
#
# Example 5: default definition, custom os repos, all-in-one csapAutoPlay (morrisville ldap and kubernetes)
# csap-01.root - -noPrompt -ignorePreflight -overwriteOsRepos -csapAutoPlay -runCleanUp -deleteContainers  -installCsap default -csapDefinition default
# 
#
#
# Example 6: default definition, public-all-in-one csapAutoPlay, skip kernel and host transfer file 
# csap-01.root - -noPrompt -ignorePreflight -csapAutoPlay -runCleanUp -deleteContainers  -installCsap default -csapDefinition default
# 
#
   
defaultCsapInstall="-noPrompt  -installCsap default -csapDefinition 'default'"

installHost=${1:--help} ;
installAws=${2:-} ;
installOptions=${3:-$defaultCsapInstall} ;

if (( $# >=3 )) ; then
	# mac os support
	shift; shift;
	installOptions="$*" ;
fi ;

isCsapEnv1=false ;
if [[ "$installHost" == *"env-1-host-pattern"* ]] ; then isCsapEnv1=true ; fi

isCsapEnv2=false ;
if [[ "$installHost" == *"some.host.pattern"* ]] ; then isCsapEnv2=true ; fi


skipBaseOperations=false ;
if [[ "$installOptions" == *skipOs* ]] ; then
	skipBaseOperations=true ;
#	skipBaseOperations=false ; # uncomment to force transfer of host.zip
fi ;



isDoBuild=false ; # enable to use localhost artifacts

if [[ $2 == "skipBuild" ]]; then isDoBuild=false;  fi ;

function setupEnvironment() {
	
	
	echo -e "\n\nHOME: '$HOME', \n\n Working Directory: '$(pwd)' \n\n PATH: '$PATH' \n\n release: '$(uname -a 2>&1)'"

	scriptDir=$(pwd) ;
	
	csapEnvFile="not-found" ;
	if [ -e installer/csap-environment.sh ] ; then
		csapEnvFile="$scriptDir/installer/csap-environment.sh" ;
		ENV_FUNCTIONS=$scriptDir/installer/functions ;
	
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
	print_two_columns "pwd" "$(pwd)" ;

  gitFolder="$HOME/git";
  local distPath="$(dirname -- $0)/../../artifacts-release/csap-host-install/build/distributions"
	print_two_columns "distPath" "'$distPath'" ;
  csapDefaultDistFolder=$(realpath $distPath) ;

  csapLocalBuildFolder="${csapLocalBuildFolder:-$csapDefaultDistFolder}";

  csapInstallerPath="$csapLocalBuildFolder/csap-host-*.zip";

  print_two_columns "csapInstallerPath" "$csapInstallerPath"
  if ! test -f $csapInstallerPath ; then
    print_error "Failed to locate csap-host.zip"
    exit 99 ;
  fi ;

  buildDir="$HOME/localbuild"
}

setupEnvironment ;


if [[ "$installOptions" != *updateOs* ]] ; then
  print_super "-updateOs parameter not found: no updates to os will occur"
  delay_with_message 5 "proceeeding with install"
fi ;

delay_with_message 1 "resuming"

# change timer to 300 seconds or more
release="2.0.0";

includePackages="no" ; # set to yes to include dev lab artifacts
includeMavenRepo="no" ; # set to yes to include maven Repo
scpCopyHost="do-not-copy"





function ensureToolsInstalled() {
	# gcc  gcc-c++ openssl097a nethogs iftop
	#local osPackages="tar zip unzip nmap-ncat dos2unix psmisc net-tools wget dos2unix sysstat lsof yum-utils bind-utils" ;
	local osPackages="wget" ;
	print_line "Verifying required packages are installed: $osPackages"
	
	for package in $osPackages ; do
		
		if is_need_package $package ; then
			yum -y  install $package
			print_line "\n\n"
		fi ;
		
	done
}

#ensureToolsInstalled ;

function getLatestInstaller() {

	mkdir --parents --verbose $csapLocalBuildFolder
	rm --verbose --recursive $csapLocalBuildFolder/*

}


#getLatestInstaller ;
#exit;

function build_notes() {
	add_note "start"
	add_note "Notes:"
	
	add_note "# remote-install-sh <hostName with certificate trust to root>"
	add_note ""
	
	add_note "Configure ssl certs:"
	add_note "$note_indent optional: use of ~/.ssh/config enables aliasing and certs to be stored. eg:"
	add_note "\nHost csap-01.root\n\tHostName centos1\n\tUser root"
	add_note ""
	
	add_note "1. Configure local host ssl:"
	add_note " # ssh-keygen -t rsa"
	add_note " # chmod 700 .ssh ; chmod 600 ~/.ssh/id_rsa"
	add_note ""
	
	add_note "2. Configure remote host ssl:"
	add_note " # scp ~/.ssh/id_rsa.pub root@csap-01:"
	add_note " # ssh root@centos1"
	add_note " # mkdir .ssh ; chmod 700 .ssh ; cat id_rsa.pub >> ~/.ssh/authorized_keys ;chmod 600 ~/.ssh/authorized_keys"
	add_note " # verify: should NOT prompt for password:"
	add_note " # 	ssh root@centos1"
	add_note " # 	ssh csap-01.root"
	add_note " # Critical: ssh will pompt the first time: you MUST ssh using the alias to set: ssh csap-01.root"
	add_note ""
	
	add_note "For more information on ssl configuration: https://wiki.centos.org/HowTos/Network/SecuringSSH"
	
	add_note ""
	
	add_note "windows users: use of choco package management and install of git includes bash as part of install"
	add_note "refer to: https://chocolatey.org/"
	
	add_note "end"
}

if [ "$installHost" == "-help" ] ; then 
	build_notes
	print_line "$add_note_contents"
	exit ;
fi ;

print_separator "remote install parameters"
print_two_columns "installHost" "'$installHost'"
print_two_columns "installAws" "'$installAws'"
print_two_columns "isCsapEnv2" "'$isCsapEnv2'"
print_two_columns "isCsapEnv1" "'$isCsapEnv1'"
print_two_columns "skipBaseOperations" "$skipBaseOperations"
print_two_columns "csapInstall" "$installOptions"


print_separator "script hardcoded settings"
print_two_columns "scriptDir" "$scriptDir"
print_two_columns "isDoBuild" "$isDoBuild"
print_two_columns "~/.ssh/config" "$(echo $(grep -A 3 $installHost ~/.ssh/config))"

verifyConnectionMessage="\n\t Hint: verify ssh configuration. Typically ssh aliases are used for tunnelling and credentials."

function remoteCopy() {

	local sourceItems="$1" ;
	local destinationFolder="$2" ;

  print_separator "remoteCopy to host: '$installHost'"

	for sourceItem in $sourceItems ; do
		print_two_columns "source" "'$sourceItem'"
		print_two_columns "dest" "'$destinationFolder'"
		scp -r -O $sourceItem $installHost:$destinationFolder

		local returnCode="$?"
    if [ $returnCode != 0 ] ; then
      print_two_columns "retry" "scp legacy mode, -O removed"
		  scp -r $sourceItem $installHost:$destinationFolder
    fi
	  exit_on_failure "$?" "Failed to copy item using scp. $verifyConnectionMessage"

	done ;
}

sshParams="";
function remoteCommand() {

  #
  #  leverages $HOME/.ssh/config for permissions
  #
	
	print_separator "$installHost: $*"
	ssh $sshParams $installHost $*

	exit_on_failure "$?" "Failed to copy item using scp. $verifyConnectionMessage"
	
}


function set_up_remote_host_for_install() {

	print_separator "cleaning up previous installs" ;
#	run_remote "rocky" "file:$HOME/.ssh/csap-test.pem" "rocky-01.aws.org" \
#	  "ls -l ; id"
	remoteCommand "ls -l" ;

	remoteCommand 'rm -rf ./installer csap-auto-play.yaml csap-test.pem settings.xml'
	if ! $skipBaseOperations ; then
		remoteCommand 'rm -rf ./csap*.zip'
	fi ;
	
	print_separator "Copying latest installer files, scriptDir: '$scriptDir', pwd is '$(pwd)'"
	remoteCopy "$scriptDir/installer"

	if test -d "$scriptDir/environment" ; then
	  remoteCopy "$scriptDir/environment/*" installer
  else
    print_two_columns "skipping" "not found $scriptDir/environment"
  fi ;

  if [[ "$installHost" == "aws-01.aws" ]] ; then
    print_section "$installHost found - running aws default setup"
    remoteCopy "$HOME/.ssh/csap-test.pem" ;
    remoteCommand sudo chmod 444 csap-test.pem
    remoteCommand sudo yum --assumeyes install nmap

    print_two_columns "Note" "dcibProviderTemplate requires a second ebs volume /data/dcib-csap-storage"
#    remoteCommand sudo mkdir --parents /data/csap-mongo-storage
#    remoteCommand sudo chmod 777 /data/csap-mongo-storage
  fi ;

  if $isCsapEnv1 ; then
    local unzipForWpc="$HOME/wpc/unzip"
    if test -f $unzipForWpc ; then
      delay_with_message 3 "Using local unzip because unzip not available in wpc"
      remoteCopy "$unzipForWpc" ;
    else
      delay_with_message 5 "Did not find $unzipForWpc, install may fail because hosts do not include it and have yum disabled"
    fi ;

  fi ;
	
	remoteCommand "chmod -R 755 installer"
	

	if [[ "$installHost" == *csap-01.root* ]] ; then

	  if test -f $HOME/.m2/settings.xml ; then
	    remoteCopy $HOME/.m2/settings.xml settings.xml ;
    fi ;
	  if test -f $HOME/.gradle/gradle.properties ; then
	    remoteCopy $HOME/.gradle/gradle.properties gradle.properties ;
    fi ;
  fi ;


	if [[ "$installOptions" == *csapAutoPlay* ]] \
		|| [[ "$installOptions" == *autoPlaySourceFile* ]]  ; then
	
		print_separator "csap-auto-play" ;
		
		if $isCsapEnv2 || $isCsapEnv1 ; then
      csapVmTemplate ;
    elif [[ "$installHost" == *"aws-01"* ]] ; then
      dcibProviderTemplate ;
		else
      kubernetesSingleTemplate
		fi ;
		
	fi ;
	
	
	if ! $skipBaseOperations ; then
#		csapZip="$csapLocalBuildFolder/csap*.zip" ;
#		if $isDoBuild ; then
#			csapZip="$HOME/temp/*.zip";
#		fi ;
		
		remoteCopy "$csapInstallerPath" ;
	fi ;
	
	remoteCommand ls -l
}


function dcibProviderTemplate() {

  local sourceFile="$scriptDir/auto-plays/dcib-provider-auto-play.yaml"
  local installerFile="$HOME/remote-play.yaml"

  cp --force --verbose $sourceFile $installerFile

  local appName="Sample CSAP at Amazon"
  local apiToken="csap-dcib" ; # must match csap-host-install
  replace_all_in_file '$appName' "$appName" $installerFile
  replace_all_in_file '$appId' "$(to_lower_hyphen $appName)" $installerFile
  replace_all_in_file '$envId' "dcib-installer" $installerFile
  replace_all_in_file '$apiToken' "$apiToken" $installerFile
  elasticIp="ec2-11-22-33-44.us-west-2.compute.amazonaws.com"
  replace_all_in_file '$elasticIp' "$elasticIp" $installerFile

#  replace_all_in_file '$managerHost' "csap-01" $installerFile
#  replace_all_in_file '$hostDomain' "csap.org" $installerFile

  remoteCopy $installerFile "csap-auto-play.yaml" ;
}

function kubernetesSingleTemplate() {

  local sourceFile="$scriptDir/auto-plays/all-in-one-auto-play.yaml"
  local installerFile="$HOME/remote-play.yaml"

  cp --force --verbose $sourceFile $installerFile

  local appName="all-in-one-remote"
  replace_all_in_file '$appName' "$appName" $installerFile
  replace_all_in_file '$appId' $(to_lower_hyphen $appName) $installerFile
  replace_all_in_file '$envId' $(to_lower_hyphen $appName) $installerFile
  replace_all_in_file '$apiToken' $(to_lower_hyphen $appName) $installerFile

#  replace_all_in_file '$managerHost' "csap-01" $installerFile
#  replace_all_in_file '$hostDomain' "csap.org" $installerFile

  remoteCopy $installerFile "csap-auto-play.yaml" ;
}

function csapVmTemplate() {
  local sourceFile="$scriptDir/auto-plays/yourCompany/template-auto-play.yaml";

  if $isCsapEnv2 ; then
    sourceFile="$scriptDir/auto-plays/yourCompany/template-simple-auto-play.yaml";
  elif $isCsapEnv1 ; then
    sourceFile="$scriptDir/auto-plays/yourCompany/template2-auto-play.yaml";
  fi ;

  local installerFile="$HOME/remote-play.yaml"

  cp --force --verbose $sourceFile $installerFile

#		replace_all_in_file "xxxHost" "csap-01" $installerFile


  local appName="csap-performance"
  replace_all_in_file '$appName' "$appName" $installerFile
  replace_all_in_file '$appId' $(to_lower_hyphen $appName) $installerFile
  replace_all_in_file '$envId' $(to_lower_hyphen $appName) $installerFile
  replace_all_in_file '$apiToken' $(to_lower_hyphen $appName) $installerFile

  replace_all_in_file '$managerHost' "$(echo "$installHost" | cut -d"." -f1)" $installerFile
  replace_all_in_file '$hostDomain' "$(expr "$installHost" : '[^.][^.]*\.\(.*\)')" $installerFile

  remoteCopy $installerFile "csap-auto-play.yaml" ;
}


function root_user_setup() {
	
	
	checkHostName=$(remoteCommand hostname) ;
	if [[ $checkHostName == *.amazonaws.com ]] ; then
		print_with_head "Found amazonaws.com , root setup is already completed" ;
		return ;
	fi ;
	
	print_separator  "host setup"
	
	stripRoot="-root"
	
	originalAlias="$installHost" ;
	installHost=${installHost%$stripRoot} ;
	#if [ "$installHost" != "$originalAlias" ] ; then

	if [ "$installAws" == "installAws" ] ; then



		print_with_head "Setting up root user using alias: $installHost derived from $originalAlias" ;
		
	
	else
		print_line "Skipping aws certificate setup" ;
	fi ;
	
	#print_with_head "Installing unzip and wget, the remaining packages and kernel configuration will be installed by csap installer"
	#remoteCommand yum --assumeyes install wget unzip 
	#remoteCommand systemctl restart chronyd.service
}


function remote_csap_install() {
	
	
	if [[ "$installHost" != *root ]] ; then
		
		sshParams="-t"
		remoteCommand sudo installer/install.sh $installOptions ;

		
	else
		remoteCommand installer/install.sh $installOptions ;
		
		if [[ "$installOptions" == *csapVmUser* ]] ; then
			
			print_section "enter remote password for copying cert from local ~/.ssh/id_rsa.pub to vm"
			scp ~/.ssh/id_rsa.pub csap-02.user:
			
			print_section "enter remote password for chmoding  cert from ~/.ssh/id_rsa.pub"
			ssh -t csap-02.user "mkdir .ssh ; chmod 700 .ssh ; cat id_rsa.pub >> ~/.ssh/authorized_keys ;chmod 600 ~/.ssh/authorized_keys;restorecon -Rv /home/$USER"
		
		fi ;
		
	fi ;


	# -skipKernel
}


#exit ;

function add_local_packages() {
	
	sourceFolder="$gitFolder/$1" ; destination="$STAGING/packages/$2"
	
	[ ! -e $sourceFolder ] && print_with_head "skipping: $sourceFolder..." && return ; # delete if exists
	
	print_with_head "overwriting $destination  with contents from $sourceFolder"
	
	ls -l $destination
	\cp -vf $sourceFolder $destination
	
	sed -i "" 's=.*version.*=<version>6.desktop</version>=' "$destination.txt"
	
	ls -l $destination
	
}


function build_csap_using_local_packages() {

	
	print_with_head "Building $release in  $buildDir - port to latest"
	exit 99
	
	if [ ! -e "$csapLocalBuildFolder" ] ; then
		print_with_head "Did not find csapLocalBuildFolder: $csapLocalBuildFolder. This needs to contain a base csap.zip" 
		exit
	fi ;
	
	
	[ -e $buildDir ] && print_with_head "removing existing $buildDir..." && rm -r $buildDir ; # delete if exists
	
	export STAGING="$buildDir/csap-platform" ;
	
	print_with_head "Extracting contents of base release $csapLocalBuildFolder/csap-host-*.zip to $buildDir ..."
	unzip -qq -o "$csapLocalBuildFolder/csap-host-*.zip" -d "$buildDir"
	
	print_with_head "Replacing $STAGING/bin with contents from $gitFolder/packages/csap-package-linux/platform-bin"
	cp -f $gitFolder/packages/csap-package-linux/platform-bin/* $STAGING/bin
	
	#print_with_head "Replacing $STAGING/mavenRepo with contents from $HOME/.m2"
	#cp -rvf $HOME/.m2/* $STAGING/mavenRepo
	
	add_local_packages packages/csap-package-java/target/*.zip Java.zip
	add_local_packages packages/csap-package-linux/target/*.zip linux.zip
	add_local_packages csap-core/csap-core-service/target/*.jar CsAgent.jar
	#exit;
	
	$scriptDir/build-csap.sh $release $includePackages $includeMavenRepo $scpCopyHost
	
	
	
	#$STAGING/bin/mkcsap.sh $release $includePackages $includeMavenRepo $scpCopyHost
	
	#includePackages="yes" ; # set to yes to include dev lab artifacts
	#includeMavenRepo="yes" ; # set to yes to include maven Repo
	#release="$release-full"
	
	#print_with_head Building $release , rember to use ui on csaptools to sync release file to other vm
	#$STAGING/bin/mkcsap.sh $release $includePackages $includeMavenRepo $scpCopyHost

}

if [ $release != "updateThis" ] ; then
	
	if $isDoBuild ; then
		build_csap_using_local_packages ;
	fi ;
	#exit ;
	
	if ! $skipBaseOperations; then
		root_user_setup ;
	fi ;
	
	set_up_remote_host_for_install 
	
	remote_csap_install
	
else
	print_with_head update release variable and timer
fi

