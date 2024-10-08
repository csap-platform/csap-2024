#!/bin/bash

# csap-environment files are cloned into install folder in build
ENV_FUNCTIONS=$(realpath $scriptDir)/functions;
source $scriptDir/csap-environment.sh


# 
isForcePackages=false;
isPrompt=true ;
isDocker="false" ;
userDelaySeconds=1 ;


#csapVM user
isCsapHostVm=false;
csapVmUser="" ;

# repo cli support

configureRepos=false;
csapBaseRepo=${csapBaseRepo:-http://media.lab.xxx.net/media/third_party/linux/CentOS/xxx-CentOS-7-Base.repo} ;
csapEpelRepo=${csapEpelRepo:-http://media.lab.xxx.net/media/third_party/linux/CentOS/xxx-epel-7.repo} ;

#
#  Core installation defaults
#
isSkipOsConfiguration=true ;
isIgnorePreflight=false ;
isRunUninstall=false ;
isRunCleaner=false ;
isServicePreKill=false ;
isDeleteContainers=false ;
isHardUmounts=false;
isSkipAutoStart=false ;
isCsapAutoPlay=false ;
autoPlaySourceFile="${autoPlaySourceFile:-/root/csap-auto-play.yaml}"

isSkipAgent="0"
isSmall="0"
csapDefinition="default"
csapUser="csap"
sudoGroup="none" ;

if [[ "$(hostname --long )" == *"some.host.pattern"* ]] ; then
  sudoGroup="qualys" ;
fi ;

installHome="/data" # installHome="/opt"
isMemoryAuth="0"
allInOnePackage="";

#
#
#
osPackages="jq tar zip unzip nmap-ncat dos2unix psmisc net-tools wget dos2unix sysstat lsof bind-utils" ;
packageCommand="yum" ;
if is_package_installed dnf ; then 
	osPackages="$osPackages dnf-plugins-core python3-dnf-plugins-core python3-dnf-plugin-versionlock" ; # use dnf autoremove
	packageCommand="dnf";
else 
	osPackages="$osPackages yum-utils yum-plugin-remove-with-leaves yum-plugin-versionlock" ;
fi


#
# systemd
#
csapServiceName="csap.service";
csapServiceFile="/etc/systemd/system/$csapServiceName" ;

# logical volume name

CSAP_VOLUME_GROUP="csap_volume_group" ;
CSAP_VOLUME_DEVICE="/dev/$CSAP_VOLUME_GROUP" ;
CSAP_INSTALL_VOLUME="csapUserLV" ;
CSAP_EXTRA_VOLUME="extraLV" ;
CSAP_ACTIVEMQ_VOLUME="activeMqLV" ;
CSAP_ORACLE_VOLUME="oracleLV" ;

installDisk="default"
# fs sizes - note they are only used if selected and they are shown as offsets
# dev numbers are determined by run order below
csapFs=0;  # always need to leave  1Gb free
extraDevice="none"
extraDiskSizeInGb=0;
extraDisk=""
mqFs=0;  
targetFs=0;
skipDisk=0;
hostTimeZone="" ; # timeZone 
oracleFs=0; moreSwap=0 # SGA configured in oracle scripts :  oracleSga=14; 

sshCertDir=""

#preserve original params to pass to child scripts
origParams="$*"

packageServer="notSpecified"
mavenSettingsUrl="default" # nimbus.xml, customer.xml, etc
mavenRepoUrl="default" # nimbus.xml, customer.xml, etc

userPassword="password";

fsType="ext4" # ext4 or xfs ? kuberntes might prefer ext4

#rhVersion=`cat /etc/redhat-release | awk '{ print $7 $8}'`

# default open files on gen2. Can be overridden as a install param
maxOpenFiles=16384;
maxThreads=4096;


function processCommandline() {

	print_super "command: '$0'"
	print_line "Parameters: '$*'"
	
	while [ $# -gt 0 ]
	do
	  case $1
	  in

	    -overwriteOsRepos )
			print_if_debug "-overwriteOsRepos specified "  
			configureRepos=true ;
			shift 1
	      ;;

	    -ignorePreflight )
			print_if_debug "-runCleanUp specified "  
			isIgnorePreflight=true ;
			shift 1
	      ;;

	    -uninstall )
			print_if_debug "-uninstall specified "  
			isRunUninstall=true ;
			isDeleteContainers=true ;
			isRunCleaner=true ;
			shift 1
	      ;;

	    -runServicePreKill )
			print_if_debug "-runServicePreKill specified "
			isServicePreKill=true ;
			shift 1
	      ;;


	    -runCleanUp )
			print_if_debug "-runCleanUp specified "  
			isRunCleaner=true ;
			shift 1
	      ;;
	      
      	-deleteContainers )
			print_if_debug "-deleteContainers specified "  
			isDeleteContainers=true ;
			isRunCleaner=true ;
			shift 1
	      ;;
	      
      	-hardUmounts )
			print_if_debug "-hardUmounts specified "  
			isHardUmounts=true ;
			shift 1
	      ;;
	      
      	-skipAutoStart )
			print_if_debug "-deleteContainers specified "  
			isSkipAutoStart=true ;
			shift 1
	      ;;
	      
      	-csapAutoPlay )
			print_if_debug "-csapAutoPlay specified "  
			isCsapAutoPlay=true ;
			shift 1
	      ;;
	      
	      
	      	  
	    -autoPlaySourceFile )
	      print_if_debug "-csapDefinition was specified,  Parameter: $2"   ;
		  isCsapAutoPlay=true ;
	      autoPlaySourceFile="$2"
	      shift 2
	    ;;
	      	  
	    -csapDefinition )
	      print_if_debug "-csapDefinition was specified,  Parameter: $2"   ;
	      csapDefinition="$2"
	      shift 2
	    ;;

	    -installCsap )
	      print_if_debug "-installCsap was specified,  Parameter: $2"   ;
	      csapFs="$2" ;
	      shift 2
	    ;;

	    -installDisk )
			print_if_debug "-installDisk specified,  Parameter: $2 " ;
			installDisk="$2" ;
			shift 2
			;;
				      
	    -targetFs )
	      print_if_debug "-targetFs was specified,  Parameter: $2"   ;
	      targetFs="$2" ;
	      shift 2
	    ;;
	    	    
	    -samplewithParam )
	      print_if_debug "-samplewithParam was specified,  Parameter: $2"   ;
	      samplewithParam="$2" ;
	      shift 2
	    ;;
	    
	    -sampleNoParam )
	      print_if_debug "-sampleNoParam specified "  
	      samplewithParam="yes";
	      shift 1
	      ;;
	    
	    -allInOnePackage )
	      print_if_debug "-allInOnePackage specified "  
	      allInOnePackage="-full";
	      shift 1
	      ;;
	    
	    -updateOs )
	      print_if_debug "-updateOs specified, kernel and os packages will updated "
	      isSkipOsConfiguration=false;
	      shift 1
	      ;;
	    
	    -forcePackages )
	      print_if_debug "-updateOs specified, kernel and os packages will be skipped "  
	      isForcePackages=true;
	      shift 1
	      ;;
	      
	    
	    -skipOs )
	      print_if_debug "-skipOs specified, kernel and os packages will be skipped "  
	      isSkipOsConfiguration=true;
	      shift 1
	      ;;
	    
	    -csapVmUser )
	      print_if_debug "-csapVmUser was specified,  Parameter: $2"   ;
		  isCsapHostVm=true;
	      csapVmUser="$2" ;
	      shift 2
	    ;;
	    
	    -zone )
	      print_if_debug "-zone was specified,  Parameter: $2"   ;
	     hostTimeZone="$2" ;
	      shift 2
	    ;;
	    
	    -pass )
	      print_if_debug "-pass was specified"   ;
	      userPassword="$2" ;
	      shift 2
	    ;;
	    
	    -maxOpenFiles )
	      print_if_debug "-maxOpenFiles was specified,  Parameter: $2"   ;
	      maxOpenFiles="$2" ;
	      shift 2
	    ;;
	    
	    -maxThreads )
	      print_if_debug "-maxThreads was specified,  Parameter: $2"   ;
	      maxThreads="$2" ;
	      shift 2
	    ;;
	    
	    -packageServer )
			print_if_debug "-packageServer specified,  Parameter: $2 " ;
			packageServer="$2" ;
			shift 2
	      ;;
	    
	    -mavenSettingsUrl )
			print_if_debug "-mavenSettingsUrl specified,  Parameter: $2 " ;
			mavenSettingsUrl="$2" ;
	      shift 2
	      ;;
	    
	    -mavenRepoUrl )
			print_if_debug "-mavenRepoUrl specified,  Parameter: $2 " ;
		 mavenRepoUrl="$2" ;
	      shift 2
	      ;;
	      
	
	    -sshCertDir )
	      print_if_debug "-sshCertDir was specified,  Parameter: $2"   ;
	      sshCertDir="$2" ;
	      shift 2
	    ;;
	          
	    -installOracle )
	      print_if_debug "-installOracle was specified,  Parameter: $2"   ;
	      oracleFs="$2" ;
	      shift 2
	    ;;
	    
	    -moreSwap )
	      print_if_debug "-moreSwap was specified,  Parameter: $2"   ;
	      moreSwap="$2" ;
	      shift 2
	    ;;
	      
	      
	    -extraDisk )
	      print_if_debug "-extraDisk was specified,  Parameter: $2 , Parameter $3 , Parameter $4"   ;
	    	extraDevice="$2" ;
	    	extraDisk="$3" ;
	    	extraDiskSizeInGb="$4" ;
	    	shift 4
	    ;;
	    
	    
	    -fsType )
	      print_if_debug "-fsType was specified,  Parameter: $2"   ;
	      fsType="$2" ;
	      shift 2
	    ;;
	      
	    -installActiveMq )
	      print_if_debug "-installActiveMq was specified,  Parameter: $2"   ;
	      mqFs="$2" ;
	      shift 2
	    ;;
	      
	    -n | -noPrompt )
	      print_if_debug "-noPrompt specified "  
	      isPrompt=false ;
	      shift 1
	      ;;
	      
      	-dockerContainer )
			print_if_debug "-dockerContainer specified "  
			isDocker="true" ;
			shift 1
	      ;;
	      

	    -memoryAuth )
	      print_if_debug "-memoryAuth specified "  
	      isMemoryAuth="1" ;
	      shift 1
	      ;;
	      
	    -s | -small )
	      print_if_debug "-sampleNoParam specified "  
	      isSmall="1" ;
	      shift 1
	      ;;
	      
	      
	    
	    -csapUser )
	      print_if_debug "-csapUser was specified,  Parameter: $2"   ;
	      csapUser="$2"
	      shift 2
	    ;;
	    
	    -sudoGroup )
	      print_if_debug "-sudoGroup was specified,  Parameter: $2"   ;
	      sudoGroup="$2"
	      shift 2
	    ;;
	    
	    -installHome )
	      print_if_debug "-installHome was specified,  Parameter: $2"   ;
	      installHome="$2"
	      shift 2
	    ;;
	    
	    *)
	    	print_line "Current Parameter: '$1'"
	      	print_with_head "usage $0 optional: -csapDefinition <host>  -csapUser <user>"
			print_line "\t help : show help"
			print_line "\t csapDefinition - host to retrieve csap definition from. Default: use the default definition, or defaultMinimal"
			print_line "\t csapUser - default: csapUser. user acount to be created, and installed"
			print_line "\t installHome - location of user account"
			print_line "\t Reference: https://github.com/peterdnight/csap-packages/tree/master/csap-package-linux/installer"
			
			print_with_head "Exiting"
			exit ;
	      shift 1
	    ;;
	  esac
	done
	
	print_two_columns "csapUser" "$csapUser"
	print_two_columns "sudoGroup" "$sudoGroup"
	print_two_columns "scriptDir" "$scriptDir"
	
	print_two_columns "installHome" "$installHome"
	print_two_columns "isSkipOsConfiguration" "$isSkipOsConfiguration"

	#
	#  .csapEnvironment created by  installer-csap-user
	#
	CSAP_FOLDER=$installHome/$csapUser/csap-platform
	#STAGING=$installHome/$csapUser/csap-platform
	#PROCESSING=$installHome/$csapUser/csap-platform/working
	print_two_columns "CSAP_FOLDER" "$CSAP_FOLDER"
	
	#print_two_columns "STAGING" "$STAGING"
	#print_two_columns "PROCESSING" "$csapPlatformWorking"
	
	print_two_columns "csapDefinition" "$csapDefinition"
	
	print_two_columns "isDocker" "$isDocker"
	
	print_two_columns "isRunUninstall" "$isRunUninstall"
	print_two_columns "isDeleteContainers" "$isDeleteContainers"
	print_two_columns "isRunCleaner" "$isRunCleaner"
	
	print_two_columns "isHardUmounts" "$isHardUmounts"
	
	print_two_columns "isSkipAutoStart" "$isSkipAutoStart"
	print_two_columns "isCsapAutoPlay" "$isCsapAutoPlay"
	print_two_columns "autoPlaySourceFile" "$autoPlaySourceFile"
	

	print_two_columns "csapFs" "$csapFs"
	print_two_columns "extraDisk" "creating '$extraDisk' '$extraDiskSizeInGb'gb on '$extraDevice'"
	print_two_columns "targetFs" "$targetFs"
	print_two_columns "fsType" "$fsType"
	print_two_columns "Prompts" "$isPrompt"
	
	if [ "$userPassword" == "password" ] ; then
		print_two_columns "WARNING" "-pass was NOT specified - using default" ;
	fi ;
		
	if $isCsapAutoPlay ; then
		if ! test -f $autoPlaySourceFile ; then 
			print_error "-isCsapAutoPlay specified, but failed to locate '$autoPlaySourceFile'" ;
			exit 99 ; 
		fi ;

		if grep '$managerHost' $autoPlaySourceFile &>/dev/null ; then
		  print_separator "Found unresolved variables in $autoPlaySourceFile"
			replace_all_in_file '$managerHost' "$(hostname --short)" $autoPlaySourceFile
      local myDomain="$(expr "$(hostname --long)" : '[^.][^.]*\.\(.*\)')"
      replace_all_in_file '$hostDomain' "$myDomain" $autoPlaySourceFile
    fi ;
	fi
	
	print_two_columns "reloading libs" "Note: this provides shared definitions"

	csapProcessingFolder=$installHome/$csapUser
	source $scriptDir/csap-environment.sh
	print_two_columns "csapProcessingFolder" "$csapProcessingFolder"
	print_two_columns "processesThatMightBeRunning" "$processesThatMightBeRunning"


	if ! $isSkipOsConfiguration ; then
		delay_with_message 5 "Warning: OS Configuration is enabled: this includes kernel modifications. Proceed ONLY if you are certain."
	fi ;
	
}

processCommandline $*


if [ $isDocker != true ] ; then
	userDelaySeconds=3 ;
	if [ "$(hostname --long)" == "$(hostname --short)" ] ; then
		print_with_head "Warning: hostname --short is the same as --long"
		print_line "Recommended: ctrl-c the install and update host settings"
		print_line "eg. hostnamectl set-hostname centos1.csap.org"
		print_line "sleeping for 30 seconds before resuming installation"
		delay_with_message $userDelaySeconds "Installation resuming" ; 
	fi ;
fi ;



function wgetWrapper() {
	print_with_head wgetWrapper: $*
	if [ $packageServer != "notSpecified" ] ; then
		wget $*
	else
		echo skipping wget because -packageServer not specified. If found in /root it will be copied
		wname="/root/"`basename $1`
		if [ -f "$wname" ] ; then
			echo found $wname
			cp $wname .
		fi
	fi ;
}



function verify_csap_package_server() {
	if [ $packageServer != "notSpecified" ] ; then
	
		print_with_head testing connectivity using command: wget $packageServer --tries=1 --timeout=3
	
		sleep 2
		wgetWrapper $packageServer  --tries=1 --timeout=3
		if [ $? -gt 0 ]; then
		    print_with_head "Error: Failed to connect to '$packageServer'. Verify server is up and accessible from host."
		    #exit 99 ;
		    print_line "Sleeping for 10 seconds, then installation will continue"
		    sleep 10;
		fi
		
	fi
}

verify_csap_package_server





function doesUserExist() {

	local userId="$1" ;
	
    /bin/egrep -i "^$userId:" /etc/passwd 2>&1 >/dev/null
    local returnCode=$? ;
    
    if (( $returnCode == 0 )) ;  then
        return 0
    else
        return 1
    fi
}


function checkgroup() {

	local checkResults=$(/bin/egrep -i "^${1}" /etc/group 2>&1) ;
    local returnCode=$? ;
    if (( $returnCode == 0 )) ;  then
		return 0
    else
		return 1
    fi
	
}


function creategroup() {
	if checkgroup ${1}; then
		echo "==  group ${1} exists"
	else
		echo "== Creating group ${1}"
		/usr/sbin/groupadd ${1}
	fi
}

function create_user() {

	local userId="$1" ;

	if doesUserExist $userId ; then
		print_two_columns "user exists" "$userId, deleting"
		/usr/sbin/userdel $userId
	 fi
	 
	print_two_columns "creating" "user $@"
	/usr/sbin/adduser $@
	echo -e "$userPassword\n$userPassword" | passwd $userId

}

function setupHomeDir() {

	local userId="$1" ;
	local homeDir="$2" ;

	
	if [ "$userId" != "" ] ; then
	
		print_two_columns "$userId" "creating $scriptDir/simple.bashrc and $scriptDir/simple.bash_profile"
	
		\cp -f $scriptDir/simple.bashrc $homeDir/.bashrc
		
		\cp -f $scriptDir/simple.bash_profile $homeDir/.bash_profile
		
		sed -i "s/CSAP_FD_PARAM/$maxOpenFiles/g" $homeDir/.bash_profile
		sed -i "s/CSAP_THREADS_PARAM/$maxThreads/g" $homeDir/.bash_profile
		
		chown -R $1 $homeDir
		chgrp -R $1 $homeDir
	fi ;
}




function createDisk() {

  if ! is_package_installed lvm2 ; then
    print_separator "Installing lvm2" ;
    yum --assumeyes install lvm2 ;
  fi ;


  print_command "vgdisplay / lvdisplay" \
    "$( vgdisplay ; lvdisplay)"

	diskVolume=$1
	diskSize=$2
	diskDevice="$CSAP_VOLUME_DEVICE/$diskVolume" ;
	
	# subtracting 1 from 1024 as sizing begins at 0. csapFS is specified in GB
	lvSize=$((diskSize*1024-5))M

	print_with_prompt "creating logical volume (lvcreate): diskGroup: $CSAP_VOLUME_GROUP, diskVolume: $diskVolume,  diskSize: $lvSize"


  print_command "lvcreate -L"$lvSize" -n$diskVolume $CSAP_VOLUME_GROUP" \
    "$( lvcreate -L"$lvSize" -n$diskVolume $CSAP_VOLUME_GROUP )"
	# mke2fs $CSAP_VOLUME_DEVICE/$CSAP_INSTALL_VOLUME

  print_command "umount $diskDevice" \
    "$( umount $diskDevice)"

	delay_with_message 5 "Creating $fsType on  $diskDevice (mkfs) "

	if [ "$fsType" == "ext4" ] ; then
		# journaled fs

    print_command "Running: mkfs -t $fsType -j $diskDevice" \
      "$( mkfs -t $fsType -j $diskDevice)"

	else 
		#mkfs -t $fsType $diskDevice ;

    print_command "Running: mkfs.xfs -f  $diskDevice" \
      "$( mkfs.xfs -f  $diskDevice )"

	fi ;

	exit_on_failure "$?" "Failed to complete: mkfs"

  print_command "Filesystem create completed $diskDevice" \
    "$( ls -l $diskDevice )"


  print_command "vgdisplay / lvdisplay" \
    "$( vgdisplay ; lvdisplay)"

	sleep 3		
}



function targetInstall() {

	print_with_head "Minor update required - replace STAGING with CSAP_FOLDER"
	return 99 ;
	
	print_with_prompt "Running targetInstall to: '$targetFs/csap',  running admin-kill-all.sh"
	admin-kill-all.sh
	
	
	print_with_prompt "stopping $APACHE_HOME/bin/apachectl stop"
	$APACHE_HOME/bin/apachectl stop

	
	installDir=`pwd`
	\rm --recursive --force $targetFs/csap
	mkdir $targetFs/csap
	cd $targetFs/csap
	
	if [ -e $HOME/csap*.zip ] ; then
		print_with_head "Found a csap install package in $HOME, adding a link"
		ln -s $HOME/csap*.zip .
	else
		print_with_head "Did not find csap zip in $HOME, it will be downloaded from tools server"
	fi ;
	
	STAGING=$targetFs/csap/csap-platform
	PROCESSING=$targetFs/csap/processing
	
	\rm --recursive --force $HOME/.csapEnvironment

	print_with_prompt "creating Csap  $HOME/.csapEnvironment"

	echo  export STAGING=$STAGING >> $HOME/.csapEnvironment
	echo  export PROCESSING=$csapPlatformWorking  >> $HOME/.csapEnvironment
	echo  export toolsServer=$packageServer  >> $HOME/.csapEnvironment
	echo  export CSAP_NO_ROOT=yes >> $HOME/.csapEnvironment
	#echo  export ORACLE_HOME="$STAGING/../oracle" >> $HOME/.csapEnvironment
	
	\cp -f $installDir/installer/simple.bash_profile $HOME/.bash_profile
	\cp -f $installDir/installer/simple.bashrc $HOME/.bashrc
	
	sed -i "s/CSAP_FD_PARAM/$maxOpenFiles/g" $HOME/.bashrc
	sed -i "s/CSAP_THREADS_PARAM/$maxThreads/g" $HOME/.bashrc
	
	print_with_head ulimits in bashrc are commented out. Validate with your sysadmin and set as needed 
	
	sed -i "s/ulimit/#ulimit/g" $HOME/.bashrc
	
	mkdir $targetFs/csap/java
	# java package installs relative to STAGING var, create a temp version to allow to proceed 
	mkdir $STAGING
	#javaInstall
	
	\rm --recursive --force  $STAGING
	cd $targetFs/csap
	print_with_prompt "Continue with CSAP Agent Install"
	$installDir/installer/installer-csap-user.sh $*
	
	source $HOME/.bashrc
	print_with_head "to start the agent run: admin-restart.sh"
	# restartAdmin.sh
}

function csapExtraDisk() {

  print_separator "creating csap extra disk"

  
	
	if [ "$extraDisk" == "" ] ; then 
		return;
	fi


	if ((  "$extraDiskSizeInGb" > 0 )) ; then
	  createDisk $CSAP_EXTRA_VOLUME $extraDiskSizeInGb ;
  else
		print_two_columns "Bypass create" "Attempting to restore $extraDisk"
    if ! is_package_installed lvm2 ; then
      print_separator "Installing lvm2" ;
      yum --assumeyes install lvm2 ;
    fi ;

    print_command "fdisk -l" \
      "$( fdisk -l)"

    print_command "pvscan" \
      "$( pvscan ; pvscan --cache)"

    print_command "vgdisplay / lvdisplay" \
      "$( vgdisplay ; lvdisplay)"

    print_command "vgimport $CSAP_VOLUME_GROUP" \
      "$( vgimport csap_volume_group)"

    local currentAttempt=1;
    local max_poll_result_attempts=100;
    for attemptCount in $(seq $currentAttempt $max_poll_result_attempts); do
      sleep 1;
      print_line "attempt $attemptCount of $max_poll_result_attempts:  waiting for: '$CSAP_VOLUME_DEVICE/$CSAP_EXTRA_VOLUME'\n"
      if test -b "$CSAP_VOLUME_DEVICE/$CSAP_EXTRA_VOLUME" ; then
        break ;
      fi ;
    done ;
	fi

	if ! test -b "$CSAP_VOLUME_DEVICE/$CSAP_EXTRA_VOLUME" ; then
	  print_error "test -b (block device test) failed: $CSAP_VOLUME_DEVICE/$CSAP_EXTRA_VOLUME" ;
	  exit 123 ;
  fi ;

	print_two_columns "cleaning" "removing entries found in /etc/fstab $extraDisk"
	delete_all_in_file "$extraDisk" /etc/fstab
	# sed -ie "\=$extraDisk= d" /etc/fstab

	print_two_columns "updating mount file" "/etc/fstab extra disk: $extraDisk"
	append_file "$CSAP_VOLUME_DEVICE/$CSAP_EXTRA_VOLUME $extraDisk $fsType defaults 1 2" "/etc/fstab"
	# echo $CSAP_VOLUME_DEVICE/$CSAP_EXTRA_VOLUME $extraDisk $fsType defaults 1 2 >> /etc/fstab
	
	print_two_columns "creating mount" "$( mkdir --verbose --parents $extraDisk 2>&1 )"
	print_two_columns "mounting disk" "$( mount $extraDisk 2>&1 )"
	
	print_two_columns "chown" "to $csapUser:  $extraDisk"
	chown -R $csapUser $extraDisk
}

function csap_user_install() {

	print_with_prompt "Running csap_user_install"
	print_two_columns "location" "'$installHome/$csapUser' $(mkdir --parents --verbose $installHome/$csapUser)"
	
	
	
	if [ $installDisk == "default" ] ; then
		print_two_columns "partition setup" "skipped - install will occur under $installHome/$csapUser"
		
		if test -d "$installHome/$csapUser" ; then 
		
			print_two_columns "Deleting" "$installHome/$csapUser"
			\rm --recursive --force $installHome/$csapUser ;
			mkdir -p $installHome/$csapUser ;
			chown $csapUser $installHome/$csapUser
			chgrp $csapUser $installHome/$csapUser
			
		fi ;
		# create_user  $csapUser --home-dir $installHome/$csapUser
		#setupHomeDir $csapUser
		setupHomeDir $csapUser $installHome/$csapUser
		
	elif [ $installDisk == "vbox" ] ; then
		print_with_head "Skipping $csapUser partition setup. Install will occur under $installHome/$csapUser"
		# create_user  $csapUser --home-dir $installHome/$csapUser -G vboxsf
		#setupHomeDir $csapUser
		
	else
		#
		#  csapUser is created at the start for uid consistency
		#

    print_command "cleaning Up $csapUser" \
      "$( sed -ie "/$csapUser/ d" /etc/fstab )"

		createDisk $CSAP_INSTALL_VOLUME $csapFs
		
		print_two_columns "update" "/etc/fstab"
		echo $CSAP_VOLUME_DEVICE/$CSAP_INSTALL_VOLUME $installHome/$csapUser $fsType defaults 1 2 >> /etc/fstab

    print_command "cat /etc/fstab" \
      "$( cat /etc/fstab )"

		print_two_columns "removing home" "$( rm --recursive --force $installHome/$csapUser )"
		print_two_columns "creating home" "$( mkdir --parents  $installHome/$csapUser )"
		print_two_columns "mounting home" "$( mount $installHome/$csapUser )"

		setupHomeDir $csapUser $installHome/$csapUser
	
	fi ;
	
	if ! $isDeleteContainers ; then
	
		print_two_columns "restoring" "previous container services"
		
		if test -e "$HOME/.kube"  ; then
			print_two_columns "restoring" "'$HOME/.kube' to '$installHome/$csapUser'"	
			\cp --recursive --force $HOME/.kube $installHome/$csapUser
		fi ;
	
		if test -e $HOME/kubelet ; then
			print_two_columns "restoring" "'$HOME/kubelet' to '$installHome/$csapUser/csap-platform/working'"	
			mkdir --parents --verbose $installHome/$csapUser/csap-platform/working
			mv --force $HOME/kubelet $installHome/$csapUser/csap-platform/working
		fi ;
	
		if test -e $HOME/docker ; then
			print_two_columns "restoring" "'$HOME/docker' to '$installHome/$csapUser/csap-platform/working'"	
			mkdir --parents --verbose $installHome/$csapUser/csap-platform/working
			mv --force $HOME/docker $installHome/$csapUser/csap-platform/working
		fi ;
		
	fi ;
	
	if $isSkipAutoStart ; then
		local disableFile=$installHome/$csapUser/csap-auto-start-disabled ;
		print_two_columns "skipAutoStart" "creating '$disableFile'"
		touch $disableFile ;
	fi ;
	
	if $isCsapAutoPlay ; then
		local autoPlayFile=$installHome/$csapUser/csap-auto-play.yaml ;
		print_two_columns "csapAutoPlay" "$(cp --verbose $autoPlaySourceFile $autoPlayFile)" ;
	fi ;
	
	
#	numberPackagesLocal=$(ls -l csap-host-*.zip | wc -l)
#	if (( $numberPackagesLocal == 1 )) ; then
#		
#		print_two_columns "csap-host-*zip found" "proceeding to install";
#		print_two_columns "clean up" "removing $installHome/$csapUser/csap*zip"
#		\rm --recursive --force $installHome/$csapUser/csap*zip ;
#		print_two_columns "copying" 'cp -f csap-host-*zip $installHome/$csapUser';
#		\cp -f csap-host-*zip $installHome/$csapUser
#		chown $csapUser $installHome/$csapUser/csap-host-*zip
#		
#		if [[ "$csapDefinition" == *.zip ]] ; then
#			print_two_columns "found zip definition" "copying local file '$csapDefinition'"
#		 	\cp -f $csapDefinition $installHome/$csapUser
#	 	fi
#		
#	fi ;
#	
#	
#	if [ -e "$scriptDir/../version" ] ; then
#		cp -r /root/version $installHome/$csapUser
#	fi ;
	# chown -R $csapUser $installHome/$csapUser/version
	
	print_two_columns "permissions" "Updating '$installHome/$csapUser' to '$csapUser'"
	chown -R $csapUser $installHome/$csapUser
	chgrp -R $csapUser $installHome/$csapUser
	
	if [ $isSkipAgent  == "1" ] ; then
		print_with_head "skipping agent install"
		return;
	fi ;
	
	if [ "$sshCertDir" != "" ] ; then 
		print_line "certs are optional"
		\cp -f -r $scriptDir/$sshCertDir $installHome/$csapUser/.ssh
		chown -R $csapUser $installHome/$csapUser/.ssh
		chgrp -R $csapUser $installHome/$csapUser/.ssh
		chmod 700 -R  $installHome/$csapUser/.ssh
		
		print_line "selinux fix"
		chcon -R unconfined_u:object_r:user_home_t:s0 $installHome/$csapUser/.ssh/
	else
		print_two_columns "certs" "no custom certs provided"
	fi

	print_two_columns "docker" "Adding group and membership to avoid a restart if/when docker is installed"
	local dresult=$(groupadd docker 2>&1; gpasswd --add $csapUser docker) ;
	print_two_columns "result" "$(echo $dresult | tr -d '\n')" ;
	
	
	
	
	# settings permissions to run csap user install as csap
	print_separator "adding permissions for user: '$csapUser'"
	
	setfacl --recursive --modify user:$csapUser:rx $scriptDir
	getfacl --absolute-names $scriptDir
	setfacl --modify user:$csapUser:rx $scriptDir/../csap-host*.zip
	getfacl --absolute-names $scriptDir/../csap-host*.zip
	
	local parentFolder=$scriptDir ;
	while (( ${#parentFolder} > 1 )) ;do
		print_two_columns "chmod 755" "$DIR"
		setfacl --modify user:$csapUser:rx $parentFolder
		getfacl --absolute-names $parentFolder
		parentFolder=$(dirname $parentFolder)
	done
	
	print_line "\n\n switching to user '$csapUser' to launch: '$scriptDir/installer-csap-user.sh' \n\n"

	su - $csapUser -c "$scriptDir/installer-csap-user.sh $*"
	
	installReturnCode="$?" ;
	
	if (( "$installReturnCode" != 0 )) ; then
		print_error "non zero return code '$installReturnCode' from installer-csap-user.sh" ;
		exit $installReturnCode ;
	fi ;
	
	if [ $isDocker != true ] ; then
	
		# remove permissions
		print_separator "Removing permissions for user: '$csapUser'"
		setfacl --remove user:$csapUser /root
		getfacl --absolute-names /root
		
		
		cd $HOME
		
		
		csapExtraDisk
	
		registerWithSystemServices
		
		local company=$(dnsdomainname)
		if [ "$company" == "" ] ; then
			print_with_head "Info: 'dnsdomainname' did not resolve - host is being configured as a stand-alone node"
			print_line "If using a VM on windows or mac - update 'C:\Windows\System32\drivers\etc\hosts' or equivalent"
		fi ;
	
		
	
		local numMatches=$(grep  "$HOSTNAME" /etc/hosts | wc -l)
		if [ $numMatches == 0 ] &&  [ "$company" == "" ]; then 
			
			print_line " WARNING: did not find $HOSTNAME in /etc/hosts, and 'dnsdomainname' did not resolv. Recommended: add $HOSTNAME to /etc/hosts"
	
		fi
	
	fi ;

	
	local fqdn="$(hostname --long)"
	print_with_date "CSAP install complete."
	print_section "To validate: http://$fqdn:8011"


}


function registerWithSystemServices() {
	
	print_section "Linux system registration $csapServiceFile"

	\cp -f $scriptDir/etc-systemd-system-csap.service $csapServiceFile
	
	replace_all_in_file "CSAP_USER" "$csapUser" $csapServiceFile "false"
	chmod -x $csapServiceFile
	
	
	local theStartScript="$CSAP_FOLDER/bin/csap-start.sh"
	replace_all_in_file "CSAP_START_FILE" "$theStartScript" $csapServiceFile "false"
	
	
	print_if_debug "Starting csap"


  if is_package_installed dnf ; then
    print_two_columns "semanage context" "dnf is installed - adding enhanced permissions to csap-start.sh for systemctl start"
    # semanage fcontext -a -t bin_t "/opt/tomcat/bin(/.*)?"
    # semanage fcontext -a -t bin_t "$installHome/$csapUser/csap-platform/bin(/.*)?"

    local csapStartFile="$installHome/$csapUser/csap-platform/bin/csap-start.sh" ;
    print_command "semanage fcontext -a -t bin_t $csapStartFile" \
        "$(semanage fcontext -a -t bin_t $csapStartFile)"

    print_command "restorecon -r -v $csapStartFile" \
        "$(restorecon -r -v $csapStartFile)"

    print_command "csap bin selinux context" \
      "$(ls -Z $(dirname $csapStartFile))"
  else
    print_two_columns "semanage context" "dnf is not installed - assuming systemctl does not require enhanced permission"
  fi ;

	print_two_columns "systemctl daemon-reload" "$( systemctl daemon-reload 2>&1 )"
	print_two_columns "systemctl enable" "$( systemctl enable $csapServiceName 2>&1 )"
	print_two_columns "systemctl restart" "$( systemctl restart $csapServiceName 2>&1 )"

  sleep 1
  #	systemctl daemon-reload
  #	systemctl enable $csapServiceName
  ##	systemctl restart $csapServiceName
  #	systemctl start $csapServiceName
	
	print_super "systemctl status $csapServiceName"
	systemctl status $csapServiceName
	
	
}



function disable_firewall() {
	
	print_with_prompt "Verifying firewalld system service is disabled"

	if systemctl is-active firewalld ; then 
		
		print_line "firewalld systemd service is active. It is being disabled for service access - docker security is used."
		
		systemctl mask firewalld.service; 
		systemctl disable firewalld.service ;
		systemctl stop firewalld.service ; 
		systemctl status firewalld.service
		
	fi
	
}

function update_sudo_settings() {

	print_section "Updating sudo settings for user '$csapUser' sudoGroup: '$sudoGroup'"
	
	#
	#   Note: param2 is written to sudoers file
	#
	$scriptDir/install-csap-sudo.sh "$csapUser" "$CSAP_FOLDER/bin" "$(dirname $ENV_FUNCTIONS)/csap-environment.sh"

	if [ "$sudoGroup" != "none" ] ; then
		if usermod -a -G $sudoGroup $csapUser ; then
			print_two_columns "sudoGroup" "added $sudoGroup"
		else

			print_error "Failed to add $csapUser to group $sudoGroup "
			delay_with_message "5" "continuing installation"

		fi ;

		print_two_columns "adding csap to the docker group" "$(gpasswd --add csap docker)" ;
	fi ;

}

#
#
# Ref http://download.oracle.com/docs/cd/B28359_01/install.111/b32002/pre_install.htm#BABBBDGA 
# http://www.oracle-base.com/articles/11g/OracleDB11gR2InstallationOnEnterpriseLinux5.php#OracleValidatedSetup
# section 2.7 and 2.8 
#
function update_os_settings() {
	
	print_with_prompt "update_os_settings"
	
	
	print_two_columns "chronyd.service" "Verifying system date is correct, current: $(date)"
	systemctl restart chronyd.service
	sleep 2;
	print_two_columns "now" "$(date)"

	update_kernel_settings

	updated_security_limits
		
	print_separator "Reloading kernel"
	sysctl -p
	
	print_line "Any errors and you may need to do: sed -i 's/csapChanged//g' /etc/sysctl.conf"
	
}

function update_kernel_settings() {
	
	print_with_prompt "updating kernel settings"
	
	local kernelConfigFile="/etc/sysctl.conf" ;
	
	if [  -e $kernelConfigFile.orig ] ; then
	
		print_line "Restoring original settings: $( cp --force --verbose $kernelConfigFile.orig  $kernelConfigFile 2>&1)"
		
	else 
	
		 numMatches=$(grep csapChanged $kernelConfigFile | wc -w)
		 
		 if (( $numMatches != 0 )) ; then 
		 
		 	print_line "hook to ensure a clean $kernelConfigFile , copying in from $scriptDir/sysctl.conf"
		 	cp $scriptDir/sysctl.conf $kernelConfigFile
		 	
		 fi ;
		 
		print_line "Back up kernel settings:  $(cp --force --verbose $kernelConfigFile $kernelConfigFile.orig 2>&1)"
		
	fi ;
	
	append_file "#" "$kernelConfigFile" true
	append_line "# added by '$0' "
	append_line "#"
	
	#echo == Removing previous comments in $kernelConfigFile
	#sed -i "s/$replaceComment//g" $kernelConfigFile 
	
	# previous instances will be commented out
	comment_out_and_append "fs.file-max = 6815744" $kernelConfigFile
	comment_out_and_append "fs.suid_dumpable = 1" $kernelConfigFile
	# comment_out_and_append "fs.aio-max-nr = 1048576" $kernelConfigFile
	#comment_out_and_append "net.ipv4.ip_local_port_range = 32768 61000" $kernelConfigFile
	comment_out_and_append "net.ipv4.ip_local_port_range = 9000 65500" $kernelConfigFile
	comment_out_and_append "net.ipv4.ip_local_reserved_ports = 10000-11000,30000-32767" $kernelConfigFile
	
	comment_out_and_append  "net.core.rmem_default=4194304" $kernelConfigFile
	comment_out_and_append  "net.core.rmem_max=4194304" $kernelConfigFile
	comment_out_and_append  "net.core.wmem_default=262144" $kernelConfigFile
	comment_out_and_append  "net.core.wmem_max=1048586" $kernelConfigFile
	
	comment_out_and_append  "kernel.msgmni = 2878" $kernelConfigFile
	comment_out_and_append  "kernel.msgmax = 8192" $kernelConfigFile
	comment_out_and_append  "kernel.msgmnb = 65536" $kernelConfigFile
	comment_out_and_append  "kernel.sem = 250 32000 100 142" $kernelConfigFile
	comment_out_and_append  "kernel.shmmni = 4096" $kernelConfigFile
	comment_out_and_append  "kernel.shmall = 5368709120" $kernelConfigFile
	comment_out_and_append  "kernel.shmmax = 21474836480" $kernelConfigFile
	comment_out_and_append  "kernel.sysrq = 1" $kernelConfigFile
	comment_out_and_append  "fs.aio-max-nr = 3145728" $kernelConfigFile
	comment_out_and_append  "vm.min_free_kbytes = 51200" $kernelConfigFile
	comment_out_and_append  "vm.swappiness = 10" $kernelConfigFile	
	
}

##
## usage: comment_out_and_append "prop=value" filename
replaceComment="# csapChanged look at bottom of file "
function comment_out_and_append() {


	propString=$1
	propName=${propString%=*}
	
	propFile=$2
	
	count=$(grep $propName $propFile | wc -l);
	
	if (( $count > 0 )) ; then 
		sed -i "/$propName/s/^/$replaceComment/" $propFile
	fi
	
	append_file "$propString" "$propFile"
	
}

function updated_security_limits() {

	if $isDocker ; then
		print_with_head "-dockerContainer specified, skipping limits - interferes when agent is in a docker container"
		return ;	
	fi ; 	
	
	local securityLimitsFile="/etc/security/limits.conf" ;
	
	print_with_prompt "Updating $securityLimitsFile with limits for user: '$csapUser' "
	
	append_file "# $csapUser" "$securityLimitsFile" true
	delete_all_in_file "$csapUser"
	
	append_line "$csapUser              soft    nofile  $maxOpenFiles"
	append_line "$csapUser              hard    nofile  $maxOpenFiles"
	append_line "$csapUser              soft    nproc  $maxThreads"
	append_line "$csapUser              hard    nproc  $maxThreads"
	
}


function timeZoneProcessing() {
	
	print_with_prompt Running timeZoneProcessing
	
	print_command \
		"Current Time Settings" \
		"$(timedatectl)"
	

	if [ "$hostTimeZone" != "" ] ; then
		print_with_head "updating time zone: '$hostTimeZone'"
		timedatectl set-timezone $hostTimeZone
	fi ; 
	
	#
#	# Check for correct timezone entries
#	clockMatches=`grep  "Chicago" /etc/sysconfig/clock | wc -l`
#	tzLink=`readlink /etc/localtime`
#	if [[ $clockMatches == 0  ||  "$tzLink" != *Chicago ]] ; then
#		
#		print_with_head "VM Time zone WARNING: did not find Chicago in /etc/sysconfig/clock. Update manually if needed. Sample: "
#
#		print_with_head MOST CSAP VMS run in central time. ctrl-c and update
#		
#		echo == hit enter to ignore or 
#		echo == ctrl-c  and add -zone "America/Chicago"  param to auto correct to central time or
#		echo == ctrl-c and manually set to your zone
#		
#		prompt Make a choice
#		
#	fi
}

function update_os_packages() {

	print_with_prompt "update_os_packages: veryifing repos and packages"
	
	if $configureRepos ; then
		print_two_columns "-overwriteOsRepos" "WARNING: updating OS repositories" ;
		
		if is_package_installed dnf && [[ $csapBaseRepo =~  .*CentOS-7.* ]] ; then
		
			print_with_head "DNF detected and csapBaseRepo: '$csapBaseRepo' - Skipping repo update"
		
		else 
		
			print_separator "updating /etc/yum.repos.d/CentOS-Base.repo and disabling yum fastestmirror.conf"
			sed --in-place 's/^mirrorlist/#mirrorlist/' /etc/yum.repos.d/CentOS-Base.repo && \
			    sed --in-place 's/^#baseurl/baseurl/' /etc/yum.repos.d/CentOS-Base.repo && \
			    sed --in-place 's/enabled=1/enabled=0/' /etc/yum/pluginconf.d/fastestmirror.conf ;
			exit_on_failure $?;
	
	
			print_command \
				"rm --verbose --recursive --force /etc/yum.repos.d/demo-xxx*" \
				"$(rm --verbose --recursive --force /etc/yum.repos.d/demo-xxx*)"
				
			if is_need_package yum-utils ; then
				print_two_columns "install yum-utils" "yum --assumeyes install yum-utils" ;
				yum --assumeyes install yum-utils
				exit_on_failure $?;
			else 
				print_two_columns "install yum-utils" "already installed" ;
			fi ;
			
			
			print_two_columns "disabling repos" "base updates extra" ;	
		    disableOutput=$(yum-config-manager --disable base) ;
			exit_on_failure $? "$disableOutput";
			
		    disableOutput=$(yum-config-manager --disable updates) ;
			exit_on_failure $? "$disableOutput";
			
		    disableOutput=$(yum-config-manager --disable extras) ;
			exit_on_failure $? "$disableOutput";
				
			add_repo_with_setup_checks "$csapBaseRepo" ;
			
			add_repo_with_setup_checks "$csapEpelRepo" ;
		fi;
		
	fi ;
	
	localVmInstall
	
	add_repo_with_setup_checks
	
	# gcc  gcc-c++ openssl097a nethogs iftop

	print_line "Verifying required packages are installed: $osPackages"
	
	local packageInstallOutput="";
	local packageReturnCode=0 ;
	for package in $osPackages ; do
		
		if is_need_package $package ; then
			
			packageInstallOutput=$($packageCommand --assumeyes  install $package 2>&1) ;
			packageReturnCode=$?
			if (( packageReturnCode != 0 )) ; then
				print_error "Failed to add $package ($packageReturnCode)" ;
				print_separator "$packageCommand output" ;
				print_line $packageInstallOutput ;
			
			else
				print_two_columns "added package" "$package" ;
			fi ;
		else
			print_two_columns "already installed" "$package" ;
		fi ;
		
	done
	
    print_separator "update_os_packages completed"
}

function localVmInstall() {
	
	print_separator "Checking for local VM host settings"
		#
	# Virtual Box: IP Address setup 
	#
#	if [[ $(hostname --long) == "centos1.lab.net" ]] ; then
#		# some envs will not resolve vm names
#		local numOccurences=$(grep -o centos1 /etc/hosts | wc -l)
#
#		if (( $numOccurences == 0 )) ; then
#			print_with_head "Adding $(hostname --long) to /etc/hosts as a localhost alias" ;
#			sed --in-place --null-data  "s/localhost/$(hostname --long) $(hostname --short) localhost/" /etc/hosts ;
#		else
#			print_two_columns "csap public" "Found centos1 in /etc/hosts - skipping add"
#		fi ;
#	fi ;
	for devHost in csap-01 csap-02 ; do
	
		print_two_columns "devHost" "$devHost"
		local fullHost="$devHost.csap.org" ;
		print_two_columns "fullHost" "$fullHost" 
		
		if [[ "$(hostname)" == "$fullHost" ]] ; then
			# some envs will not resolve vm names
			local numOccurences=$(grep -o "$devHost" /etc/hosts | wc -l)
	
			if (( $numOccurences == 0 )) ; then
				print_with_head "VM Detection: Adding $(hostname) to /etc/hosts as a localhost alias" ;
				sed --in-place --null-data  "s/localhost/$(hostname) $(hostname --short) localhost/" /etc/hosts ;
			else
				print_two_columns "skipping update" "$devHost is already in /etc/hosts"
			fi ;
		fi ;
	done
	

#	if [ "$csapDefinition" == "defaultPublic" ] ; then 
#		print_two_columns "csap public"  "defaultPublic definition: configuring internet repo"
#		print_two_columns "csap public" "disabling all repos, then enabling base and extras, output suppressed"
#		
#		yum-config-manager --disable \* > /dev/null
#		yum-config-manager --enable base > /dev/null
#		yum-config-manager --enable extras > /dev/null
#
#		if [[ $(hostname --short) == "centos1" ]] ; then
#			# some envs will not resolve vm names
#			local numOccurences=$(grep -o centos1 /etc/hosts | wc -l)
#	
#			if (( $numOccurences == 0 )) ; then
#				print_with_head "Adding $(hostname --long) to /etc/hosts as a localhost alias" ;
#				sed --in-place --null-data  "s/localhost/$(hostname --long) $(hostname --short) localhost/" /etc/hosts ;
#			else
#				print_two_columns "csap public" "Found centos1 in /etc/hosts - skipping add"
#			fi ;
#		fi ;
#	fi ;
}

function aws_install() {
	
	external_host="$(curl -s http://11.22.33.44/latest/meta-data/public-hostname)"
	
	# redhat
	hostnamectl set-hostname --static $external_host
	sed -i "/preserve_hostname/d" /etc/cloud/cloud.cfg
	echo -e "\npreserve_hostname: true\n" >> /etc/cloud/cloud.cfg
}



function clean_up_previous_installs() {
	
	print_with_prompt "clean_up_previous_installs()"
	
	preKillMatches=$(clean_up_process_count) ;
	



	if $(is_process_running csapProcessId=csap-agent) ; then
	  print_separator "Shutting down csap-agent" ;
    print_two_columns "disabling csap.service" "$( systemctl disable $csapServiceName 2>&1 )"

  	if $isServicePreKill ; then
      delay_with_message $userDelaySeconds "EXPERIMENTAL: isServicePreKill is $isServicePreKill, doing killall $csapUser" ;
      killall --verbose --user $csapUser ;

    else
  #    print_line "Stopping csap: $( systemctl stop $csapServiceName; systemctl disable csap 2>&1 )" ;
      print_two_columns "stopping csap.service" "$( systemctl stop $csapServiceName 2>&1 )"
    fi ;

  else
    print_section "Did not csap-agent running, bypassing agent cleanup"
  fi;


	
	
	if ! $isRunCleaner ; then
		print_with_head "Host clean is being skipped" ;
		if (( $preKillMatches > 0 )) ; then
			print_two_columns "Warning" "found matching processes; use -runCleanUp to remove"
			delay_with_message $userDelaySeconds "Installation resuming" ; 
		fi
		return ; 
	fi
	
	if $isHardUmounts ; then
		hard_umount_all
	fi ;
	
	
	if $isDeleteContainers ; then

		if $(is_package_installed kubeadm) ; then
			clean_kubernetes
		fi ;
		
		if $(is_package_installed docker-ce) ; then
			clean_docker
			print_separator "Removing docker folder contents" ;
			umount_containers /var/lib/docker ;
			\rm --recursive --force /var/lib/docker/*
		fi ;
		
		if $(is_package_installed podman) ; then
			clean_podman_and_crio
			print_separator "Removing docker folder contents" ;
			
			if test -L /var/lib/containers  ; then
		
				print_command \
					"removing link $podmanStorage " \
					"$(rm --verbose /var/lib/containers)"
					
			fi ;
			
			umount_containers /run/containers/storage
		
			umount_containers /var/lib/docker ;
			\rm --recursive --force /var/lib/docker/*
		fi ;
		
	else
		print_line "-deleteContainers not specified"
		
		if test -e $installHome/$csapUser/csap-platform/working/kubelet ; then
			print_line "Found '$installHome/$csapUser/csap-platform/working/kubelet', moving up to $HOME"
			rm --recursive --force $HOME/kubelet
			mv --force $installHome/$csapUser/csap-platform/working/kubelet  $HOME
		fi ;
		
		if test -e $installHome/$csapUser/csap-platform/working/docker ; then
			print_line "Found '$installHome/$csapUser/csap-platform/working/docker', moving up to $HOME"
			rm --recursive --force $HOME/docker
			mv --force $installHome/$csapUser/csap-platform/working/docker  $HOME
		fi ;
		
	fi ;
	

	
#	print_separator "Running $packageCommand clean all"
#	$packageCommand clean all ; rm -rf /var/cache/yum
	
	
	print_separator "Checking for additional processes to be stopped" ;
#	if $(is_process_running csapProcessId=csap-agent) ; then
#		print_line "Stopping csap: $( systemctl stop $csapServiceName; systemctl disable csap 2>&1 )" ;
#	fi ;
	
	# csap.service should be killing agent and all launched children. This is insurance
	local processKillItems="$csapProcessingFolder"
	
	if $isDeleteContainers ; then
	
		processKillItems="$processesThatMightBeRunning"
		local killUsers="$csapUser csapUser";
		
		for killUser in $killUsers ; do
			if (( $(id -u $killUser 2>&1 | wc -w) == 1 )) ; then 
				print_two_columns "user $killUser"  "Running killall and pkill on user"
				killall --verbose --user $killUser
				pkill -9 --euid $killUser ; 
			fi
		done ;
	
	fi
	
	for processItem in $processKillItems ; do
	
		matches="$(pgrep --list-full --full $processItem)" ;
	
		if [[ "$matches" == "" ]] ; then
		
			
			print_two_columns "$processItem" "no matches found" ;
			
		else
		
			print_command \
			  "WARNING: Running pkill --full '$processItem'" \
			  "$matches"
			  
			pkill -15 --full "$processItem" ;
			
			sleep 1 ;
			
			pkill -9 --full "$processItem" ;
		
		fi ;
		
	done ;
	

	
	local postKillMatches=$(clean_up_process_count) ;
	print_separator "Pre clean process matches: '$preKillMatches', post clean: '$postKillMatches' "
	
	if (( $postKillMatches > 0 )) ; then
		print_line "Warning: found matching processes after clean up. Install will resume in 10 seconds. (ctrl-c to manually clean)"
		delay_with_message $userDelaySeconds "Installation proceeding" ;
	fi
	
	
	
	
	if [ "$extraDevice" != "none" ] && (( $extraDiskSizeInGb > 0 )); then 
		initialize_csap_volume_group $extraDevice;
  else
    initialize_csap_volume_group $installDisk ;
	fi
	
}



function remove_csap_volume_group() {

	local linuxRawDevice="$1" ;

  print_command "fdisk -l" \
    "$( fdisk -l)"

  if ! is_package_installed lvm2 ; then
    print_separator "Installing lvm2" ;
    yum --assumeyes install lvm2 ;
  fi ;

  print_command "logical volumes via vgdisplay" \
    "$( vgdisplay)"


  for filename in $CSAP_VOLUME_DEVICE/*; do

    print_separator "Wiping disk signatures: wipefs -f -a $filename"
    wipefs -f -a $filename

  done

	# umount -fl l=lazy
	#fuser -k $extraDisk
	#umount -fl $extraDisk
	#lvremove -ff $CSAP_VOLUME_DEVICE/$CSAP_EXTRA_VOLUME

  print_command "killing processes using fuser -k $installHome/$csapUser" \
    "$( fuser -k $installHome/$csapUser )"

	# fuser -k $installHome/mquser ;
	if [ "extraDisk" != "" ] ; then
    print_command "killing processes using fuser -k $extraDisk" \
      "$( fuser -k $extraDisk )"
	fi ;

	print_two_columns "Unmounting" "$( umount -f $extraDisk $installHome/$csapUser 2>&1 )"
	delete_all_in_file "$CSAP_VOLUME_GROUP" /etc/fstab

	local numberOfCsapFileSystems=$( df -h | grep $CSAP_VOLUME_GROUP |  wc -l)
	if (( numberOfCsapFileSystems > 0 )) ; then
		print_section " CSAP filesystems still mounted: $numberOfCsapFileSystems" ;

    print_command "df -h  | grep $CSAP_VOLUME_GROUP" \
      "$( df -h  | grep $CSAP_VOLUME_GROUP )"

		print_error "verify all filesystems are unmounted and removed using 'umount ...'"
		exit 58 ;
	fi;

  if test -e $CSAP_VOLUME_DEVICE/swapLV ; then
	  print_two_columns "Disabling swap" "$( swapoff $CSAP_VOLUME_DEVICE/swapLV 2>&1 )" ;
	fi ;

  if test -e /dev/shm  ; then
	  print_two_columns "removing shared memory" "$( umount /dev/shm 2>&1 )" ;
	  delete_all_in_file '/dev/shm' /etc/fstab
	fi ;

	# sed -ie '/\/dev\/shm/ d' /etc/fstab

	print_separator "Running dmsetup to remove volumes"

	print_two_columns "removing extra volume" \
	  "$( dmsetup remove $CSAP_VOLUME_DEVICE-$CSAP_EXTRA_VOLUME 2>&1 )"

	print_two_columns "removing install volume" \
	  "$( dmsetup remove $CSAP_VOLUME_DEVICE-$CSAP_INSTALL_VOLUME 2>&1 )"

	print_two_columns "dmsetup ls" \
	  "$( dmsetup ls 2>&1 )"


	print_separator "cleaning up $CSAP_VOLUME_GROUP"

	print_two_columns "Disabling volumegroup" \
	  "$( vgchange -a n $CSAP_VOLUME_GROUP 2>&1 )"

	print_two_columns "Removing volumegroup" \
	  "$( vgremove -f $CSAP_VOLUME_GROUP 2>&1 )"

  print_command "vgdisplay $CSAP_VOLUME_GROUP" \
    "$( vgdisplay $CSAP_VOLUME_GROUP )"


	if (( $? != 0 )) ; then
		print_error "Error displaying volume group: $CSAP_VOLUME_GROUP" ;

    print_command "verify all volumes are unmounted and removed, try 'vgdisplay ...'" \
      "$( dmsetup ls | grep $CSAP_VOLUME_GROUP )"

		exit 56 ;
	fi;

	local numberOfCsapVolumes=$( dmsetup ls | grep $CSAP_VOLUME_GROUP |  wc -l )
	if (( numberOfCsapVolumes > 0 )) ; then
		print_error "Too many volume groups: $CSAP_VOLUME_GROUP, volumes remaining: $numberOfCsapVolumes" ;

    print_command "verify all volumes are unmounted and removed, try 'dmsetup remove ...'" \
      "$( dmsetup ls | grep $CSAP_VOLUME_GROUP )"

		exit 57 ;
	fi;

  print_command "Scanning disks pvscan --cache" \
    "$( pvscan --cache )"

  print_command "vgdisplay / lvdisplay" \
    "$( vgdisplay ; lvdisplay)"

	#
	# Check for required disk
	print_line  "$linuxRawDevice : completed the removal"

}

function create_csap_volume_group() {

	local linuxRawDevice="$1" ;

	if ! test -e "$linuxRawDevice" ; then
		if test -e /dev/vdb  ; then
			print_with_head " WARNING: installDisk set to /dev/vdb   because specified disk $linuxRawDevice does not exist" ;
			installDisk="/dev/vdb"

      print_command "fdisk -l" \
        "$( fdisk -l)"

			sleep 3 ;
		else
      print_command "fdisk -l" \
        "$( fdisk -l 2>&1 )"

			print_error " MAJOR ERROR: $linuxRawDevice not found - supplementary disk is needed by CS-AP installer" ;
			print_error " Use VM dashboard to confirm second disk is displayed, and confirm with fdisk -l " ;
			exit 99;
		fi ;
	fi;


  print_section "Creating partion /sbin/parted --script $linuxRawDevice"
	/sbin/parted --script "$linuxRawDevice" mklabel msdos

	#print_with_head Wiping disk signature from $linuxRawDevice
	#wipefs -a $linuxRawDevice

 	# this only works if all lvs are deleted first pvremove -y -ff "$linuxRawDevice"


  print_command "issueing pvcreate $linuxRawDevice" \
    "$( pvcreate -ff -y "$linuxRawDevice" 2>&1 )"

  print_command "creating volume group vgcreate $CSAP_VOLUME_GROUP $linuxRawDevice" \
    "$( vgcreate $CSAP_VOLUME_GROUP "$linuxRawDevice" 2>&1 )"

  print_command "vgdisplay / lvdisplay" \
    "$( vgdisplay ; lvdisplay)"

	delay_with_message "5" "Verify volume groups, confirm creating of $CSAP_VOLUME_GROUP"

}

function initialize_csap_volume_group() {

	local linuxRawDevice="$1" ;

  if [ $linuxRawDevice == "default" ] || [ $linuxRawDevice == "vbox" ] ; then
    # print_two_columns "partition" "Skipping partition cleanup."
    return ;
  fi ;

  print_with_prompt "initialize_csap_volume_group: volume group will wiped and recreated"

  print_two_columns "CSAP_VOLUME_GROUP" "$CSAP_VOLUME_GROUP"
  print_two_columns "linuxRawDevice" "$linuxRawDevice"

  delay_with_message 3 "remove_csap_volume_group"
  remove_csap_volume_group "$linuxRawDevice"

	delay_with_message 3 "create_csap_volume_group"
  create_csap_volume_group "$linuxRawDevice"
}

function uninstallCsap() {

	clean_up_previous_installs
	
	if test -d $installHome/$csapUser ; then
		print_with_prompt "removing '$installHome/$csapUser'" ;
		\rm --recursive --force $installHome/$csapUser ;
	fi ;
	
	if test -d /opt/java ; then
		print_with_prompt "removing '/opt/java'" ;
		\rm --recursive --force /opt/java ;
	fi ;
	
	
	if test -d $(pwd)/installer ; then
		print_with_prompt "removing '$(pwd)/installer'" ;
		\rm --recursive --force $(pwd)/installer ;
	fi ;
	
	if test -f $csapServiceFile ; then
		print_with_prompt "removing '$csapServiceFile'" ;
		systemctl stop $csapServiceName ;
		systemctl disable $csapServiceName ;
		rm --verbose $csapServiceFile ;
		systemctl daemon-reload
		systemctl reset-failed
	fi ;
	
	if doesUserExist $csapUser ; then
	
		print_separator "cleanup of user '$csapUser'"
		print_two_columns "facl" "for safety remove any facls on /"
	
		getfacl /
		setfacl --remove user:$csapUser /
		setfacl -bn /
		print_two_columns "/usr/sbin/userdel" "deleting '$csapUser' "
		/usr/sbin/userdel $csapUser
	
	fi ;
	
	run_preflight true "csap post uninstall" ;
	
}

function coreInstall() {

  print_command "processes listing (pre cleanup)" "$(ps -ef | grep -v '\[')"

	clean_up_previous_installs ;
	
	print_command "processes listing  (post cleanup)" "$(ps -ef | grep -v '\[')"

	run_preflight $isIgnorePreflight;

	print_with_head "OS Installation" ;

  if cat /proc/mounts | grep 'hidepid' &> /dev/null ; then
    print_command "cat /proc/mounts" "$(cat /proc/mounts)"
    delay_with_message 10 "Warning: hidepid found - this will not allow csap-agent to locate docker containers or root processes"
  fi
	
	
	if ! doesUserExist $csapUser ; then
		print_separator "creating csap user '$csapUser' to ensure consistent uid"
		mkdir --parents $installHome/$csapUser
		create_user $csapUser --home-dir $installHome/$csapUser

	else
		print_two_columns "user exists" "'$csapUser', skipping create"
	fi
	
	if $isCsapHostVm ; then
	
		localVmInstall ;
		
		print_separator "creating csapVmUser user '$csapVmUser' to emulate labs"
		mkdir --parents /home/$csapVmUser
		create_user $csapVmUser --home-dir /home/$csapVmUser
		
		setupHomeDir $csapVmUser /home/$csapVmUser

		local csapVmBash="/path/to/bash_profile";
		print_section "Creating '$csapVmBash'"
		mkdir --parents --verbose $(dirname $csapVmBash)
		append_file "#" "$csapVmBash" true
		append_line "export csapVmHome=\"/home/$csapVmUser\""


		print_section "updating sudoers for user"
		append_file "#" "/etc/sudoers" true
		append_line "root    ALL=(ALL)       ALL"
		append_line "peter.nightingale    ALL=(ALL)       ALL"
		
		yum install epel-release -y
		
		
		print_section "Update .ssh/config to use csap-XX.user"
		
		
		exit ;

	fi ;
	
	update_sudo_settings

	delay_with_message 3
	
	
	if $isSkipOsConfiguration ; then
	
		print_separator "isSkipOsConfiguration" ;
		print_line "skipping configuration of kernel,  security limits and os_package installation" ;
		
		if $isForcePackages ; then
			print_two_columns "isForcePackages" "$isForcePackages" ;
			update_os_packages
		
		else
		  if [[ "$(hostname --long)" == *"some.host.pattern"* ]] ; then
        print_section "dos2unix is being installed. For full packages, use -forcePackages "
        $packageCommand --assumeyes  install dos2unix
      fi ;
      local zipOverFile="$scriptDir/../unzip"
      if test -f $zipOverFile ; then
        delay_with_message 5 "Found $zipOverFile" ;

        if test -f /usr/bin/unzip ; then
          print_two_columns "found existing" "/usr/bin/unzip" ;

        else
          print_two_columns "adding" "/usr/bin/unzip" ;
          cp --verbose $zipOverFile /usr/bin
          chmod 755 /usr/bin/unzip
          print_command "verifying unzip command" "$(unzip -v)"
        fi ;
      fi ;
		fi ;
		
		print_line "Verifying required packages are installed: $osPackages"
	
		local packageInstallOutput="";
		local packageReturnCode=0 ;
		local foundMissingPackage=false;
		for package in $osPackages ; do
			
			if is_need_package $package ; then
				print_two_columns "missing" "$package" ;
				foundMissingPackage=true;
			else
				print_two_columns "installed" "$package" ;
			fi ;
			
		done ;
		
		if $foundMissingPackage ; then
			print_error "Warning missing packages. Either include OS settings or pre-install";
			delay_with_message $userDelaySeconds "Installation proceeding" ;
		fi
		
	else 
	
		timeZoneProcessing
	
		print_separator "configuring /etc/bashrc"
		
		if [  -e /etc/bashrc.orig ] ; then
			cp /etc/bashrc.orig  /etc/bashrc
			
		else 
			 
			print_two_columns "backing up"  "/etc/bashrc to /etc/bashrc.orig"
			cp /etc/bashrc /etc/bashrc.orig ;
		fi ;
		
		# get a simple bash profile first into root
		\cp -f $scriptDir/simple.bash_profile $HOME/.bash_profile
		\cp -f $scriptDir/simple.bashrc $HOME/.bashrc
		
		#sed -i "s/CSAP_FD_PARAM/4096/g" $HOME/.bashrc
		replace_all_in_file "CSAP_FD_PARAM" "$maxOpenFiles" $HOME/.bash_profile "true"
		
		delete_all_in_file "CSAP_THREADS_PARAM"  $HOME/.bash_profile "true"
		
		disable_firewall
	
		update_os_settings
		
		update_os_packages
	
		if [ "$moreSwap" != "0" ] ; then
			swapoff $installHome/oracle/swapfile
			#echo == creating swap for oracle 
			#dd if=/dev/zero of=$installHome/oracle/swapfile bs=1024 count=$((1048576*$moreSwap))
			#mkswap $installHome/oracle/swapfile
			#swapon $installHome/oracle/swapfile
			swapSize=$((moreSwap*1024-5))M
			lvcreate -L"$swapSize" -nswapLV $CSAP_VOLUME_GROUP
			mkswap $CSAP_VOLUME_DEVICE/swapLV
			# get rid of previouse
			sed -ie '/swapLV/ d' /etc/fstab
			echo $CSAP_VOLUME_DEVICE/swapLV swap swap defaults 0 0 >> /etc/fstab
			swapon -va
		fi
		
	fi ;
	
	cd $HOME

}
