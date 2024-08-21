# csap host install,  requires: root ssh;  runs install on 1 or more hosts using a specified definition



#
#     To run:
#		- update configure function 
#		- Sample:  definitionProvider="default" ; hostsToInstall="csap-dev20" ; remotePassword=xxxx ; 
# 

function configure() {

  aws_configure           # assumes rocky linux, uses ssh -i <certfile>

  # general_configure     # general purpose, uses sshpass for install

}

function aws_configure() {
	definitionProvider="default";
	
	hostsToInstall="xxx1 xxx2 ..." ; 		  # one or more host short name space delimited
	
	remoteUser="rocky" ;                               # rocky for rockylinux, ec2-user from amazon linux
	remotePassword="file:/home/rocky/csap-test.pem"    # ssh -i used for install
	
	runInForeground=false ;				             # install output placed in /root/csap-install.txt
	isKillContainers=true ;				             # if true all docker,kubernetes, and containers are reinstalled
	isCleanJournal=true ;				               # cleaning up system logs makes troubleshooting much easier
	extraOptions="-updateOs -ignorePreflight"  # install os packages, use root partition for docker,kubele
	
	#
	# aws distribution host and application template
	#
  elasticIp="ec2-11-22-33-44.us-west-2.compute.amazonaws.com"

  # default: install performance templates
  appName="CompanyXXX CSAP at Amazon" ;
  envName="demo-env" ; includeTemplates="do-not-include" ;

  # kubernetes: uncomment
  # appName="csap-kubernetes-demo" ; includeTemplates="includeTemplates"


  # === if pasting from setup, paste ABOVE this LINE
  #
  generate_autoplay_template "$appName" "$hostsToInstall" "$includeTemplates"
	
	
	
	#
	# install media: using the csap-host.zip used to install current host, alternately use maven repo
	#
	csapZipUrl="http://${csapFqdn:-$(hostname --long)}:${agentPort:-8011}/api/agent/installer"

}


function general_configure() {
  	definitionProvider="${definitionProvider:-default}";			# a running host, or default for a new application

  	hostsToInstall="${hostsToInstall:-my-host-1 my-host-2}" ; 		# one or more host short name space delimited

  	remoteUser="root" ;                 # rocky for aws
  	remotePassword="YOUR_PASSWORD" ;    #  sshpass used for install
  	# remotePassword="file:/home/rocky/csap-test.pem" # aws uses credentials

  	runInForeground=false ;				# install output placed in /root/csap-install.txt
  	isKillContainers=true ;				# if true all docker,kubernetes, and containers are reinstalled
  	isCleanJournal=true ;				  # cleaning up system logs makes troubleshooting much easier

  	# -skipOs : bypass repo checks, kernel parameters, and security limits
  	# -ignorePreflight : bypass configuration validation
  	# -uninstall: remove csap
  	# -updateOs updates os packages and more
  	extraOptions="" ; # aws  extraOptions="-updateOs -ignorePreflight"


  	#
  	# optional:  customize an existing (or default) csap application definitions.
  	#
  	installerFile="disabled" ;

    elasticIp="none" ; # elasticIp="ec2-11-22-33-44.us-west-2.compute.amazonaws.com"
  	# generate_autoplay_template "My App Name" "$hostsToInstall" "includeTemplates"



  	#
  	# install media: using the csap-host.zip used to install current host, alternately use maven repo
  	#
  	csapZipUrl="http://${csapFqdn:-$(hostname --long)}:${agentPort:-8011}/api/agent/installer"
  	# csapZipUrl="http://devops-prod01.csap.org:8081/artifactory/csap-release/org/csap/csap-host/22.03/csap-host-22.03.zip"
}

#
#  Note:
#		- autoplay files can be edited directly, but can also be templated as shown here
#		- refer to https://github.com/csap-platform/csap-core/wiki/Csap-Autoplay
#
function generate_autoplay_template() {

	local appName=${1} ;
	local hostsToInstall=${2} ;
	local includeTemplates=${3:-doNotInclude} ;
	
	local sourceTemplate="$CSAP_FOLDER/auto-plays/all-in-one-auto-play.yaml" ;
	
	local managerHost=$(echo $hostsToInstall | awk '{print $1}') ; 
	local workerHosts=$(echo $hostsToInstall | cut -d ' ' -f 2-) ;
	
	
	local numHosts=$(echo "$hostsToInstall" | wc -w) ;

	local apiTokenValue="$(to_lower_hyphen $appName)" ;
	if (( $numHosts > 1 )) ; then
		sourceTemplate="$CSAP_FOLDER/auto-plays/demo-auto-play.yaml" ;
		if [[ "$elasticIp" != "none" ]] ; then
		  #
		  #  AWS env
		  #
      apiTokenValue="csap-dcib"
      sourceTemplate="$CSAP_FOLDER/auto-plays/dcib-user-kubernetes-auto-play.yaml" ;
#		  if [[ "$appName" == "csap-perf-demo" ]] ; then
#        sourceTemplate="$CSAP_FOLDER/auto-plays/dcib-user-perf-auto-play.yaml" ;
#		  else
#        sourceTemplate="$CSAP_FOLDER/auto-plays/dcib-user-kubernetes-auto-play.yaml" ;
#      fi ;
		fi ;
	elif [[ "$elasticIp" != "none" ]] ; then
      apiTokenValue="csap-dcib"
      sourceTemplate="$CSAP_FOLDER/auto-plays/dcib-user-single-auto-play.yaml" ;
	fi ;
	
	
	
	local NOW=$(date +'%h-%d-%I-%M-%S') ; 
 	installerFile="$CSAP_FOLDER/auto-plays/generated/demo-$NOW.yaml" 
 	mkdir --parents $CSAP_FOLDER/auto-plays/generated
	
	print_section "Building: $installerFile" ;
	print_two_columns "managerHost" "$managerHost"
	print_two_columns "workerHosts" "$workerHosts"
	print_two_columns "sourceTemplate" "$sourceTemplate"
	print_two_columns "elasticIp" "$elasticIp"
	
	# data center in a box: dcib-auto-play.yaml
	cp --force --verbose $sourceTemplate $installerFile

	replace_all_in_file '$appName' "$appName" $installerFile
	replace_all_in_file '$appId' $(to_lower_hyphen $appName) $installerFile
	replace_all_in_file '$envId' $(to_lower_hyphen $envName) $installerFile
	replace_all_in_file '$apiToken' "$apiTokenValue" $installerFile

	replace_all_in_file '$managerHost' "$managerHost" $installerFile
	replace_all_in_file '$workerHosts' "$(comma_separate $workerHosts)" $installerFile

	if [[ "$elasticIp" != "none" ]] ; then
	  replace_all_in_file '$elasticIp' "$elasticIp" $installerFile
  fi ;

	if [[ "$(hostname --long)" == *"us-west-2.compute.internal"* ]] ; then
	  replace_all_in_file '$appName' "$appName" $installerFile
  fi ;
	
	local csapFqdn="$(hostname --long)";
	if [ -n "$dockerHostFqdn" ] && [ "$dockerHostFqdn" != "container" ] ; then 
		print_line "dockerHostFqdn is set '$dockerHostFqdn', it will be used instead of hostname: '$csapFqdn' " ;
		csapFqdn="$dockerHostFqdn" ;
	fi;
	
	local myDomain=$(expr "$csapFqdn" : '[^.][^.]*\.\(.*\)')
	replace_all_in_file '$hostDomain' "$myDomain" $installerFile
	
	if [ $includeTemplates == "includeTemplates" ] ; then
	
		replace_all_in_file 'storage-settings-nfs' "storage-settings" $installerFile ;
		replace_all_in_file 'enabled: false' "enabled: true" $installerFile ;
		
	fi
	
	print_command "$installerFile" "$(cat $installerFile)" ;
	
}


function verify_host_access() {

	print_separator "connection tests"

  if [[ "$remotePassword" == "file:"* ]] ; then
    tokenFile="${remotePassword:5}";
    print_two_columns "host access" "using certificate file" ;
    print_two_columns "user" "$remoteUser";
    print_two_columns "tokenFile" "$tokenFile";
    if ! test -f $tokenFile ; then
      print_error "Token file not found" ;
      exit 99 ;
    fi;
  else
    if $(is_need_package sshpass) ; then
      run_using_root yum --assumeyes install sshpass openssh-clients;
    fi ;

    if $(is_need_package sshpass) ; then
      # ensure epel is enabled
      run_using_root 'yum search epel-release; yum info epel-release; yum --assumeyes install epel-release'
      run_using_root yum --assumeyes install sshpass openssh-clients;
    fi ;
	  exit_if_not_installed sshpass ;
  fi ;



	local failureCount=0;

	for targetHost in $hostsToInstall; do

	  if [[ "$remotePassword" == "file:"* ]] ; then
	    hostOutput=$(ssh -i $tokenFile -o StrictHostKeyChecking=no $remoteUser@$targetHost ls -ld /etc 2>&1) ;
	  else
      hostOutput=$(sshpass -p $remotePassword ssh -o StrictHostKeyChecking=no $remoteUser@$targetHost ls -ld /etc 2>&1) ;
    fi ;

    connection_return_code="$?" ;

		print_if_debug $targetHost "$hostOutput"


		if (( $connection_return_code != 0 )) ; then
			print_two_columns "$targetHost" "FAILED"
			failureCount=$(( $podCount + 1)) ;
		else
			print_two_columns "$targetHost" "PASSED"
		fi ;

	done ;

	if (( $failureCount > 0 )) ; then

		print_error "Aborting installation - correct connection errors"
		exit $install_return_code ;

	fi

}

function remote_installer () {

	configure
	
	verify_host_access
	
	delay_with_message 10 "Installation resuming" ; 
	
	cleanupParameters="" ;
	if $isKillContainers ; then
		cleanupParameters="-deleteContainers"
	fi ;
	
	if [[ $hostsToInstall == *$(hostname --short)* ]] ; then 
		print_with_head "Aborting installation: this script must not be run on a host being installed" ; 
	fi ;
	
	
	local targetDefinition="default" ;
	
	if [[ $definitionProvider != default* ]] ; then
	
		#
		#  get the zip local - so we can do in place update of entire cluster
		#
		
		print_separator "Copying application definition to hosts"
	
		targetDefinition="/root/application.zip" ;
	
		if [ -f definitionZip ] ; then
			print_line ""
			rm --force definitionZip
		fi ;
	
		definitionUrl="http://$definitionProvider:8011/os/definitionZip"
		
		if [[ $definitionProvider =~ http.* ]] ; then
			definitionUrl=$definitionProvider ;
		fi ;
		
		print_two_columns "source" "$definitionUrl copied to $(pwd)"
		print_two_columns "local" "$(hostname --short):$(pwd)"
		
		local definitionOutput=$(wget --no-verbose --output-document definitionZip $definitionUrl 2>&1) ;
		print_two_columns "result" "$definitionOutput"
		
		definition_return_code="$?" ;
		if (( $definition_return_code != 0 )) ; then
			print_error "Failed to retrieve definition - verify definitionProvider: $definitionProvider"
			exit $definition_return_code ;
		fi ;
		copy_remote $remoteUser $remotePassword "$hostsToInstall" definitionZip /root/application.zip
		
	fi ;
	
	
	print_separator "Starting installation ..."
	sleep 5 ;
	
	
	testCommands=( "hostname --short" ) # testCommands=( "nohup ls &> ls.out &")
	
	local cleanJournalCommand="echo skipping journal cleanup" ;
	if $isCleanJournal ; then
		cleanJournalCommand='journalctl --flush; journalctl --rotate; journalctl --vacuum-time=1s;rm --recursive --force /var/log/journal/*' ;
	fi ;
	
	
	local backgroundCommand="&> csap-install.txt &";
	if $runInForeground ; then
		backgroundCommand="" ;
	fi ;
	
	local autoPlayParam=""
	if [[ "$installerFile" != "disabled"  ]] ; then 
	
		if ! test -f $installerFile ; then
			print_error "Specifed installer file not found: '$installerFile'" ;
			exit 99 ;
		fi ;
	
		print_separator "Copying $installerFile to remote hosts"

		# -autoPlaySourceFile -csapAutoPlay
	  #	autoPlayParam=" -csapAutoPlay "
		#copy_remote $remoteUser $remotePassword "$hostsToInstall" $installerFile /root/csap-auto-play.yaml

		local destAutoPlayPath="/root/csap-auto-play.yaml"
		if [[ "$remoteUser" != "root" ]] ; then
		  destAutoPlayPath="/home/$remoteUser/csap-auto-play.yaml"
		fi ;
		autoPlayParam=" -autoPlaySourceFile  $destAutoPlayPath "
		copy_remote $remoteUser $remotePassword "$hostsToInstall" $installerFile $destAutoPlayPath

	fi ;

	# ; systemctl restart chronyd.service
	local installerOsCommands="yum --assumeyes install wget unzip" ;
	if [[ "$extraOptions" == *skipOs* ]] ; then
		installerOsCommands="echo assuming wget and unzip installed" ;
	fi ;
	
	local sampleNfsCleanup=(
     'echo $(hostname --long) ;'
     "ps -ef | grep mount"
     "pkill -9 mount"
     "ps -ef | grep mount"
     "sed -i '\|/mnt/nfsshare|d' /etc/fstab"
   		)
   	
	
	#
	#  shutdown of kubernetes on all hosts FIRST ensures consistent resource state
	#  - definitely required if a csap docker nfs server is being used
	#
	if $isKillContainers ; then
	
		# reset run on all hosts in parallel

      run_remote $remoteUser $remotePassword "$hostsToInstall" \
        'ENV_FUNCTIONS=$(realpath installer)/functions;source installer/csap-environment.sh ;  perform_kubeadm_reset &> csap-clean.txt &'

      # wait on each host in turn to complete : /usr/bin/kubelet vs kube vs kubeadm
      run_remote $remoteUser $remotePassword "$hostsToInstall" \
        'ENV_FUNCTIONS=$(realpath installer)/functions;source installer/csap-environment.sh ;  wait_for_terminated "kubeadm" 120 root;  print_command "kube processes" "$(ps -ef | grep kube)" '

	
	fi
	
	local installCommands=(
	     'echo $(hostname --long) ;'
	     "$cleanJournalCommand"
	     'rm --recursive --force --verbose csap*.zip* *linux.zip installer'
	     "$installerOsCommands"
	     "wget --no-verbose --content-disposition $csapZipUrl"
	     'unzip  -j csap-host-*.zip csap-platform/packages/csap-package-linux.zip'
	     'unzip -qq csap-package-linux.zip installer/*'
	     "nohup installer/install.sh -noPrompt $autoPlayParam -runCleanUp $cleanupParameters \
	    -installDisk default  \
	    -installCsap default $extraOptions \
	    -csapDefinition $targetDefinition $backgroundCommand"
	   )
	   
	run_remote $remoteUser $remotePassword "$hostsToInstall" "${installCommands[@]}";
	
	
	
	print_separator "Installation Complete" ;
	print_line "agent dashboard will be available in 60-90 seconds";

}

remote_installer
