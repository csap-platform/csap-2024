#!/bin/bash

#
# file system
#

function show_java_rpm() {

  local fileToVerify=${1:-/path/not/specified}

  local rpmExtractFolder="$CSAP_FOLDER/temp/rpm"
  if test -d $rpmExtractFolder ; then
    run_and_format rm --recursive --force $rpmExtractFolder
  fi
  mkdir --parents $rpmExtractFolder ;

  cd $rpmExtractFolder ;

  if test -f $fileToVerify ; then

    unrpm $fileToVerify
    for warPath in $(find $rpmExtractFolder -name '*.war' ) ; do
      show_java $warPath
    done

  else

    print_error "show_java_rpm: file not found: '$fileToVerify'"

  fi ;

}

function show_java() {

  local fileToVerify=${1:-/path/not/specified}
  local allowed=${1:-17}

  if [[ "$fileToVerify" =~ \.rpm$ ]] ; then
    show_java_rpm "$fileToVerify"
    return
  fi ;

  if test -f $fileToVerify ; then

    print_super "show_java" "'$fileToVerify'"

    local extractFolder="$CSAP_FOLDER/temp/audit-java"
    if test -d $exractFolder ; then
      rm --recursive --force $extractFolder
    fi

    mkdir --parents $extractFolder/firstPass ;
    mkdir --parents $extractFolder/secondPass ;
    mkdir --parents $extractFolder/classes ;

    print_two_columns "secondPass" "destination $extractFolder/secondPass"
    if [[ "$fileToVerify" =~ \.jar$ ]] ; then
      print_two_columns "cp" "$(cp --verbose --force  $fileToVerify $extractFolder/firstPass)"
      unzip -qq -o -j $fileToVerify -d $extractFolder/secondPass ;

    else
      print_two_columns "unzip" "destination $extractFolder/firstPass"
      unzip -qq -o -j $fileToVerify -d $extractFolder/firstPass ;
    fi

    print_two_columns "numberOfJars" "$(find $extractFolder -name '*.jar' | wc -l)"

#    find $extractFolder -name '*.jar' -exec sh -c "unzip -qq -o -j -d $extractFolder/classes {}" ';'

    local warnings=() ;
    local java17Jars=() ;
    local java9thru16Jars=() ;
    local java8Jars=() ;
    local java7Jars=() ;
    local java6Jars=() ;
    local java5Jars=() ;
    local earlierJars=() ;
    local javaLaterThen17Jars=() ;
    local mrJars=() ;
    local moduleJars=() ;
    local classType ;
    local mrVersions ;
    local moduleListing ;
    for jarPath in $(find $extractFolder -name '*.jar' ) ; do

      local jarName=$(basename $jarPath) ;

      print_if_debug ""
      print_if_debug "jar" "$jarPath"

      mrVersions="$( unzip -Z1  $jarPath | grep -v 'module-info' | grep '.class' | grep 'META-INF/versions' | head -1)"
      if [[ "$mrVersions" != "" ]] ; then
        print_if_debug "mrVersions"  "$(echo $mrVersions)"
        mrJars+=($jarName)
      fi

      moduleListing="$( unzip -Z1  $jarPath | grep 'module-info' | head -1)"
      if [[ "$moduleListing" != "" ]] ; then
        print_if_debug "moduleListing"  "$(echo $moduleListing)"
        moduleJars+=($jarName)
      fi


      classFile="$( unzip -Z1  $jarPath | grep -v 'META-INF' | grep '.class' | head -1)"
      if [[ "$classFile" == "" ]] ; then
        warnings+=( $jarName )
      else
        print_if_debug "class file" "$classFile"
        unzip -qq -o -d $extractFolder/classes $jarPath $classFile

        print_if_debug "file output" "$( file $extractFolder/classes/$classFile )"
        classType=$( file $extractFolder/classes/$classFile | awk '{print $7 ;}') ;
        print_if_debug "type" "$classType"

        case "$classType" in

          "61.0" )
            java17Jars+=($jarName)
            ;;

          "62.0" | "63.0" | "64.0" | "65.0" | "66.0" )
            javaLaterThen17Jars+=($jarName)
            ;;

          "60.0" | "59.0" | "58.0" | "57.0" | "56.0" | "55.0" | "54.0" | "53.0" )
            java9thru16Jars+=($jarName)
            ;;

          "52.0")
            java8Jars+=($jarName)
            ;;

          "51.0")
            java7Jars+=($jarName)
            ;;

          "50.0")
            java6Jars+=($jarName)
            ;;

          "49.0")
            java5Jars+=($jarName)
            ;;

           *)
            earlierJars+=($jarName)
            ;;
        esac

      fi

#      print_two_columns "class file" "unzip -qq -o -j -d $extractFolder/classes {} $jarPath"
    done;

  print_array_in_columns "mrJars (containing META-INF/versions): ${#mrJars[@]}" mrJars

  print_array_in_columns "moduleJars (containing module-info): ${#moduleJars[@]}" moduleJars

  print_array_in_columns "java 18 or later: ${#javaLaterThen17Jars[@]}" javaLaterThen17Jars

  print_array_in_columns "java 17: ${#java17Jars[@]}" java17Jars

  print_array_in_columns "java 9 thru 16: ${#java9thru16Jars[@]}" java9thru16Jars

  print_array_in_columns "java 8: ${#java8Jars[@]}" java8Jars

  print_array_in_columns "java 7: ${#java7Jars[@]}" java7Jars

  print_array_in_columns "java 6: ${#java6Jars[@]}" java6Jars

  print_array_in_columns "java 5: ${#java5Jars[@]}" java5Jars

  print_array_in_columns "java other: ${#earlierJars[@]}" earlierJars

  print_array_in_columns "no classes" warnings

#    print_two_columns "numberOfClasses" "$(find $extractFolder/classes -name '*.class' | wc -l)"
#
#    print_section "Running file command to determine class version $extractFolder/java-types.text"
#    find $extractFolder/classes -name '*.class' -exec file {} \; | awk '{print $9 ;}' | tr -d ')' > $extractFolder/java-types.text
#
#    print_section "Running file command to determine class version"
#    sort  $extractFolder/java-types.text | uniq -c | sort -bgr

    #find /data/csap/csap-platform/temp/audit-java -name "*.class" -exec file {} \; | awk '{print $9 ;}' | sort | uniq -c

  else

    print_error "show_java: file not found: '$fileToVerify'"

  fi ;

}


function unrpm() {

  local rpmFile=${1:-/path/not/specified}
  local rpmShortName="unRpm-$( trim 33 $( basename $rpmFile ) )"

  if test -f $rpmFile ; then

    mkdir $rpmShortName ;
    cd $rpmShortName ;
    print_section "Extracting to folder $rpmShortName"
    rpm2cpio $rpmFile | cpio -idmv
    cd ..
  else

    print_error "unRpm: file not found: '$rpmFile'"

  fi ;

}

function trimPath() {
  local pathToTrim=${1}
  local pathCountToShow=${2:-1}

  local runCount
  local resultPath=""
  local prefix="..."
  for (( runCount=1; runCount<=$pathCountToShow; runCount++ )) ; do
    currentItem=$(basename $pathToTrim)
    pathToTrim=$(dirname $pathToTrim)
    if [[ "$resultPath" == "" ]] ; then
      resultPath="$currentItem"
    else
      resultPath="$currentItem/$resultPath"
    fi
#    echo "trimming '$pathToTrim' "
    if [[ "$pathToTrim" == "/" ]] ; then
      prefix="";
      break ;
    fi ;

  done
  echo "${prefix}/${resultPath}"
}

function show_project_depends() {
  local location=${1:-$(pwd)}
  local showAll=${2:-no}

  print_section "occurences of project in build files"

  local buildFiles=( $(find $location -name "build.gradle") )

  local numProjects

  local countLines
  for  fileName in "${buildFiles[@]}" ; do
#    print_two_columns "checking" "$fileName"
    numProjects=$(cat $fileName | grep -E 'ation project|getSomeProject\('| wc -l)

#    if (( $numProjects == 0 )) || [[ $showAll == "showAll" ]]; then
      countLines+="$( printf "%10s" $numProjects )   $(trimPath $fileName 3)\n"
#    fi ;

  done

  echo $countLines | sort -r
}

function show_build() {
  local  location=${1:-$(pwd)}
  local  minCount=${2:-10}
  local  exclude=${3:-/build/}

  local itemCount ;


  print_two_columns "location" "$location"
  print_two_columns "minCount" "$minCount"
  print_two_columns "exclude" "$exclude"
  print_separator "All File Types"

  find . -type f -not -path '*/\.*' \
    | grep -v "$exclude" \
    | sed -n 's/..*\.//p' | sort | uniq -c \
    | awk -F" " '$1+0 >= 10' \
    | sort -r \
    |  awk -F" " '{ printf "   %20s   %s \n", $2, $1 }'


  print_separator "Key Types"
  local filetypes=("java" "json" "sh" "kt" "groovy" "kts")
  for item in $filetypes ; do
#    print_separator "$item"
    itemCount=$(find . -type f -name "*.${item}" | grep -v "$exclude" | wc -l)

    if (( $itemCount > 0 )) ; then
      find . -type f -name "*.${item}" \
          | grep -v "$exclude" \
          | sed -n 's/..*\.//p' | sort | uniq -c \
          | sort -r \
          |  awk -F" " '{ printf "   %20s: %6s files", $2, $1 }'

      count_lines_in_files "*.$item"

    fi
  done

  local buildItems=( "build.gradle" "build.gradle.kts" "settings.gradle" "settings.gradle.kts" "gradle.properties" )

  print_separator "Build Items"
  for item in $buildItems ; do

    itemCount=$(find -name "$item" | wc -l)
    if (( $itemCount > 0 )) ; then
      find -name "$item" -exec basename "{}" \; \
        | uniq -c \
        |  awk -F" " '{ printf "   %20s: %6s files", $2, $1 }'

      count_lines_in_files $item
#      find -name "$item" \
#        | xargs wc -l | grep total | awk -F " " '{Total=Total+$1} END{print "    " Total " Lines" }'
      # find -name "$item" | xargs wc -l  | tail -1 | awk '{printf "%10s lines\n", $1} lines'
    else
      printf "   %20s: %6s files\n" $item 0
    fi

  done

    run_and_format 'find -wholename "*/libs/*.jar" | grep -v sources | grep -v test.jar | wc -l'
}

function count_lines_in_files() {
  local fileType=${1:-sh}
  ( find $(pwd) -type f -name "${fileType}" -print0 \
            | xargs -0 cat ) | wc -l | LC_ALL=en_US.UTF-8 awk '{printf "%'"'"'20d lines\n", $1}'


#          | xargs wc -l | grep total | awk -F " " '{Total=Total+$1} END{print "    " Total " Lines" }'
#          | xargs wc -l  | tail -1 | awk '{printf "%10s lines\n", $1} lines'
}


function remove_prefix() {
  local sourceString=${1}
  local prefix=${2}
  echo ${sourceString#"$prefix"}
}


function remove_after() {
  local sourceString="${1}"
  local suffix="${2}"
  echo ${sourceString%%${suffix}}
}

function trim() {

  local maxLength=${1:-999} ; shift 1;
  local expression="$*"
  if (( "${#expression}" > "$maxLength" )); then
    echo "${expression:0:$maxLength}"
  else
    echo "$expression"
  fi
}

function trimLeft() {


  local maxLength=${1:-999} ; shift 1;
  local expression="$*"
  if (( "${#expression}" > "$maxLength" )); then
    local length=$(echo $expression | wc -c )
    local start=$(( $length - $maxLength ))
    echo "...${expression:$start:$length}"
  else
    echo "$expression"
  fi

}


function restore_filesystem_acls() {

	print_with_head "Stripping ACLs from $(pwd) auto-added when using csap file browser. Ref https://www.computerhope.com/unix/usetfacl.htm"
	setfacl --remove-all --recursive $(pwd)

}

function make_if_needed() {

	local folderName=$1 ;
	local output=$(mkdir --parents --verbose $folderName 2>&1 | tr '\n' '  ') ;

	if [ -z "$output" ] ; then
		print_line "make_if_needed: folder exists: $folderName" ;
	else
		print_line "make_if_needed: $output" ;
	fi

}

function test_ciphers() {

	local SERVER=${1:-$(hostname --long):8082}

	print_section "testing ciphers on $SERVER using $(openssl version)" ;

	local DELAY=0.1
	local ciphers=$(openssl ciphers 'ALL:eNULL' | sed -e 's/:/ /g')

	local supportCount=0 ;

	for cipher in ${ciphers[@]} ; do

		echo -n "    $(printf '%-40s' "$cipher")"
		result=$(echo -n | openssl s_client -cipher "$cipher" -connect $SERVER 2>&1)
		if [[ "$result" =~ ":error:" ]] ; then

		  error=$(echo -n $result | cut -d':' -f6)
		  echo NO \($error\)

		else

			if [[ "$result" =~ "Cipher is ${cipher}" || "$result" =~ "Cipher    :" ]] ; then
				echo YES
				supportCount=$(( supportCount + 1 )) ;
			else
				echo UNKNOWN RESPONSE
				echo $result
			fi

		fi

		sleep $DELAY

	done

  	print_two_columns "total supported"  "$supportCount"
}

function wait_for_terminated_minutes() {

	local processName=${1} ;
	local minutes=${2:-10} ;
	local processUser=${3:-csap};
	local exitIfStillRunning=${4:-no};
	local dots;
	local iteration;
	local pidValue;

	print_separator "Waiting for user '$processUser' process '$processName' to complete"

  local processStillRunning=true ;
	for (( iteration=$minutes; iteration > 0; )) ; do

		pidValue=$(ps -u $processUser -f| grep $processName  | grep -v -e grep -e $0 | awk '{ print $2 }' | tr '\n' ' ')

		print_two_columns "$iteration minutes" "pid(s) - '$pidValue'"  ;

		if [ "$pidValue" == "" ] ; then
		  processStillRunning=false
			break ;
		fi ;

    if (( $iteration > 20 )) ; then
      iteration=$(( $iteration - 5 ))
      sleep 300 ;
    else
      iteration=$(( $iteration - 1 ))
      sleep 60;
    fi ;

	done

	if $processStillRunning ; then
	  print_error "Processes still running" ;
	  if [[ "$exitIfStillRunning" == "exitIfStillRunning" ]] ; then
	    exit 991
	  fi
	fi ;

}

function wait_for_terminated() {

	local processName=${1} ;

	local seconds=${2:-10} ;
	local processUser=${3:-csap};
	local message=${4:-continuing in};
	local dots;
	local iteration;
	local pidValue;

	#print_separator "Waiting for $processName to not be running"

	for (( iteration=$seconds; iteration > 0; iteration--)) ; do

		pidValue=$(ps -u $processUser -f| grep $processName  | grep -v -e grep -e $0 | awk '{ print $2 }' | tr '\n' ' ')
		message="Pid(s): '$pidValue', " ;
		dots=$(printf "%0.s-" $( seq 1 1 $iteration ));



		if [ "$pidValue" != "" ] ; then
			print_two_columns "found process" "$message $(printf "%3s" $iteration) seconds  $dots"  ;
		else
			print_two_columns "no process running" "$processName"  ;
			break ;
		fi ;

		sleep 1;
	done

	print_line "" ;

}


function make_keystore() {

	local domain=${1:-csap.org}
	local cred=${2:-csap1234}

	#
	#
	#

	keytool -genkeypair -noprompt \
		-alias csap \
		-dname "CN=$domain, OU=ID, O=csap, L=csap, S=platform, C=US" \
		-storetype PKCS12 \
		-keystore csap.p12 \
		-keyalg RSA \
		-keysize 2048 \
		-storepass $cred \
		-keypass $cred \
		-validity 3650

#	keytool -import -keystore csap.p12 -alias csap -file csap.pem -trustcacerts
}



#
# Installer
#
csapProcessingFolder=${csapProcessingFolder:-csap-platform};
processesThatMightBeRunning="csap/java docker containerd /usr/bin/conmon podman kubelet httpd mpstat $csapProcessingFolder" ;

function clean_up_process_count() {

	local doPrint=${1:-true} ;

	if $doPrint ; then
		print_line2 "\n\nclean_up_process_count:"
		print_two_columns2 "Process Pattern" "count" ;
	fi ;
	totalMatches=0 ;
	for processName in $processesThatMightBeRunning ; do

		matchCount=$(ps -ef | grep -v grep | grep $processName | wc -l) ;
		totalMatches=$(( $totalMatches + $matchCount )) ;
		# print to stderr so we can leverage return value
		if $doPrint ; then
			print_two_columns2 $processName $matchCount ;
		fi ;
	done ;

	if $doPrint ; then
		>&2 print_two_columns "Total" "$totalMatches" ;
		>&2 print_line "\n"
	fi ;
	sleep 1 ; #allow for stderr to be flushed

	echo $totalMatches
}

function run_preflight() {

	local isIgnorePreflight=${1:-false}
	local label=${2:-csap preflight}

	print_section "$label"

	local networkRc=$(run_preflight_network; echo $?) ;
	local osVersionRc=$(run_preflight_osVersion; echo $?) ;
	local filesystemReadabeRc=$(are_file_systems_readable; echo $?) ;
	local filesystemRc=$(run_preflight_filesystem; echo $?) ;
	local processRc=$(run_preflight_processes; echo $?) ;


	if [[ $label == *uninstall* ]] ; then
		return 0 ;
	fi ;


	if (( $networkRc > 0 )) \
		|| (( $osVersionRc > 0 )) \
		|| (( $osVersionRc > 0 )) \
		|| (( $processRc > 0 )) \
		|| (( $filesystemRc > 0 )) ; then

		print_with_head "One or more systems failed $label" ;

		if $isIgnorePreflight ; then
			print_two_columns "$label" "-ignorePreflight is set"
		else
			print_two_columns "$label" "failed, exiting. To ignore, add -ignorePreflight"
			exit 90 ;
		fi ;
	fi ;

	sleep 1 ; # allow stderr to flush

}

function run_preflight_processes() {
	local processMatches=$(clean_up_process_count false) ;

	local returnCode=0 ;

	if (( $processMatches > 0 )) ; then
		print_preflight false "process" "found $processMatches csap processes";
		local numWithPrintsEnabled=$(clean_up_process_count true);
		returnCode=92 ;
	fi


	local processCount=$(ps -ef | grep -v "\[" | wc -l) ;
	local maxLimit=55
	if (( $processCount > $maxLimit )) ; then
		print_preflight false "process" "found $processCount processes, maximum is $maxLimit ";
		returnCode=94 ;
	else
		print_preflight true "process" "found $processCount processes, maximum is $maxLimit ";
	fi

	print_preflight true "process" "no csap processes found"
	return $returnCode ;

}

function print_preflight() {

	local result=$1 ; shift 1 ;
	local category=$1 ; shift 1 ;
	local details="$*"

	if [ "$result" == "true" ] ; then result="Passed" ; else  result="Failed" ; fi

	>&2 printf "%20s   [%s]   %-s\n" $category $result "$details";
}

function run_preflight_network() {

	local networkOutput=$(ip a | grep enp0s9) ;

	if (( $(ip a | grep ens192: | wc -l) > 0 )) ; then
		print_preflight true "network" "detected ens192"

	elif (( $(ip a | grep enp0s3: | wc -l) > 0 )) ; then
		print_preflight true "network" "detected enp0s3"

	elif (( $(ip a | grep eth0: | wc -l) > 0 )) ; then
		print_preflight true "network" "detected eth0"

	else
		print_preflight false "network" "unexpected interface. Run: ip a, and ensure kubernetes calico_ip_method is set with interface=<name>"
		return 91 ;
	fi

	return 0 ;
}

function run_preflight_osVersion() {
	local releaseInfo=$(cat /etc/redhat-release 2>&1) ;
	local releaseInfoWords=(${releaseInfo})
	local releaseVersion="not-detected";

	local returnCode=0;

	if (( $(echo $releaseInfo | grep CentOS | wc -l) > 0 )) ; then
		releaseVersion="${releaseInfoWords[3]}" ;
		print_preflight true "distribution" "discovered CentOS"

	elif (( $(echo $releaseInfo | grep "Red" | wc -l) > 0 )) ; then
		releaseVersion="${releaseInfoWords[6]}" ;
		print_preflight true "distribution" "discovered Redhat"

	elif (( $(echo $releaseInfo | grep "Rocky" | wc -l) > 0 )) ; then
		releaseVersion="${releaseInfoWords[3]}" ;
		print_preflight true "distribution" "discovered Rocky"

	else
		print_preflight false "distribution" "unexpect os distribtion: '$releaseInfo'. Recommended is CentOs 7.6"
		returnCode=92 ;
	fi

	if [[ "$releaseVersion" == 7.* ]] || [[ "$releaseVersion" == 8.* ]] ; then
		print_preflight true "version" "discovered $releaseVersion"
	else
		print_preflight false "version" "unexpected os version: '$releaseVersion'. Expected 7.*"
		returnCode=92 ;
	fi ;

	local packageCount=$(rpm -qa | wc -l) ;
	local maxLimit=600
	if (( $packageCount > $maxLimit )) ; then
		print_preflight false "packages" "found $packageCount packages, maximum is $maxLimit. Recommended: CentOS Minimal ";
		returnCode=94 ;
	else
		print_preflight true "packages" "found $packageCount packages, maximum is $maxLimit ";
	fi

	return $returnCode ;
}

function run_preflight_filesystem() {

	local fileSystemInfo=$(timeout 5s df --block-size=G --print-type | sed 's/  */ /g' |  awk '{print $3 " " $7}') ;

	local allPassed=true ;

	verify_filesystem "/run" "5" "$fileSystemInfo" ;
	if (( $? != 0 )) ; then allPassed=false; fi ;

	verify_filesystem "/var/lib/docker" "50" "$fileSystemInfo" ;
	if (( $? != 0 )) ; then allPassed=false; fi ;

	verify_filesystem "/var/lib/kubelet" "25" "$fileSystemInfo" ;
	if (( $? != 0 )) ; then allPassed=false; fi ;

	verify_filesystem "/opt" "20" "$fileSystemInfo" ;
	if (( $? != 0 )) ; then allPassed=false; fi ;

	if ! $allPassed ; then
		return 93 ;
	fi

	return 0 ;
}

function verify_filesystem() {

	local mountPoint="$1";
	local minimumSize="$2";
	local fullInfo="$3";

	local filesystem=$(echo -e "$fullInfo" | grep --word-regexp "$mountPoint$" ) ;

	if [ "$filesystem" != "" ] ; then
		local fsWords=(${filesystem}) ;
		local size=${fsWords[0]::-1} ;
		local name=${fsWords[1]} ;
		if (( $size < $minimumSize )) ; then
			print_preflight false "filesystem" "$name size: '$size' is less then $minimumSize" ;
			return 94 ;
		else
			print_preflight true "filesystem" " verified $filesystem, size: $size" ;
		fi ;

	else
		print_preflight false "filesystem" "missing mountpoint '$mountPoint'" ;
		return 95 ;
	fi ;

	return 0 ;
}

function are_file_systems_readable() {

	local dfResponseCheck=$(timeout 2s df --print-type --portability --human-readable | wc -l);
	if (( $dfResponseCheck == 0 )) ; then
		print_preflight false "mounts" "unable to list mounted filesystems" ;
		return 95 ;
	fi ;

	print_preflight true "mounts" "found: $dfResponseCheck filesystems" ;
	return 0 ;

}

function hard_umount_all() {

	local procMounts="/proc/mounts" ;

	print_separator "hard_umount_all() examining $procMounts"

	local podMounts=$(cat $procMounts | grep pod | awk '{print $2}');

	for podMount in $podMounts; do
		print_two_columns "podMount" "attempting umount of $podMount"
		print_command "output" "$(umount $podMount 2>&1)"

		# deleted pods end with \040(deleted), so removing the last 13 characters
		local podLess8=${podMount::-13} ;
		print_two_columns "podMount" "attempting umount of $podLess8"
		print_command "output" "$(umount $podLess8 2>&1)"
	done ;



	local mntMounts=$(cat $procMounts | grep /mnt | awk '{print $2}');

	for mntMount in $mntMounts; do
		print_preflight "mntMount" "attempting umount of $mntMount"
		print_command "output" "$(umount -l $mntMount 2>&1)"
	done ;
}


#
# Packaging
#

# ref https://stackoverflow.com/questions/592620/how-can-i-check-if-a-program-exists-from-a-bash-script
function exit_if_not_installed() {

	local command="${1:-missing-arg}"

	if ! hash $command &> /dev/null ; then
		print_with_head "error: '$command' not found, install and try again.";
		exit;
	fi
}


function isInteractiveTerminal() {
  if test -t FD ; then
    true
  else
    false
  fi
}


function is_linux() {
	local command="${1:-missing-arg}"

	if [[ "$(uname -s 2>&1 )" == *inux ]]  ; then
		true ;
	else
		false ;
	fi ;

}


function is_command_available() {

	local command="${1:-missing-arg}"

	if hash $command &> /dev/null ; then
		true ;
	else
		false ;
	fi ;
}

function is_process_running() {

	command="$1"

	if (( $(ps -ef | grep -v grep | grep $command | wc -l )  > 0 ));  then
		true ;
	else
		false ;
	fi ;
}

function is_function_available() {

	functionName="$1"

	if [ -n "$(type -t $functionName)" ] && [ "$(type -t $functionName)" == "function" ];  then
		true ;
	else
		false ;
	fi ;
}

function printOsArchitecture() {
  if is_command_installed lscpu ; then
    echo $(lscpu | grep -i architecture | tr --squeeze-repeats ' ' | cut --delimiter=' ' --fields='2' ) ;
  else
    echo $(uname -m)
  fi
}

function printOsName() {
  if test -f /etc/os-release ; then
    echo $(source /etc/os-release && echo $NAME | tr [:upper:] [:lower:] ) ;
  elif is_command_installed sw_vers ; then
      echo $(sw_vers ) ;
  else
    echo $(uname -a)
  fi
}
# if isOsCsapSupport ; then echo yup ; fi
function isOsCsapSupport() {

  local osName=$( printOsName ) ;

	if [[ "$osName" == *amazon* ]] ; then
		false ;
	else
		true ;
	fi ;

}

function is_need_package() {

	! is_package_installed $1

}

function install_if_needed() {

	local packageName=${1} ;

	if $(is_need_package $packageName) ; then
		run_using_root yum --assumeyes install $packageName
		local returnCode=$? ;
		if (( $returnCode != 0 )) ;  then
			print_error "Warning: failed to install jq"
		fi
	fi ;


}



#
#  Common pitfall: bash local declaration sweep return codes: https://google.github.io/styleguide/shellguide.html#s7.6-use-local-variables
#
function exit_on_failure() {
	local returnCode=${1:-999} ;
	print_if_debug "returnCode: '$returnCode'"
	local message=${2:-no-reason-specified} ;


	if (( "$returnCode" == 0 )) && [ "$returnCode" != 0  ] ;  then
		print_error "Error: invalid return code: '$returnCode'. Reason: $message" ;
		exit $returnCode ;
	fi ;

	if (( "$returnCode" != 0 )) ;  then
		print_error "Error: return code: '$returnCode'. Reason: $message" ;
		exit $returnCode ;
	fi ;
}

function is_package_installed () {

	rpm -q $1 2>&1 >/dev/null

}

function is_need_command() {

	! is_command_installed $1 ;

}


function is_ok() {
  local returnCode=${1:-999} ;
  if (( "$returnCode" == 0 )) ;  then
    true
  else
    false
  fi
}

function is_command_installed() {

	local verify=$(which $1 2>/dev/null);

	if [[ "$verify" == *"not found"* ]] || [[ "$verify" == "" ]]; then
		false ;

	else
		true ;

	fi;

}

function ensure_files_are_unix() {

	updatePath=$1

	if [ -f "$updatePath" ] ; then
		updatePath="$1/*" ;
	fi ;

	if is_command_installed dos2unix ; then
		print_line "Found scripts in package, running dos2unix"
		find $updatePath -name "*.*" -exec dos2unix --quiet -n '{}' '{}' \;

	else

		print_line "Warning: did not find  dos2unix. Ensure files are linux line endings"

	fi ;

}


#
# file functions
#

function does_file_contain_word() {

	local returnCode=0;

	local targetFile="$1";
	local targetWord="$2";

	if ! grep --quiet --word-regexp "$targetWord" "$targetFile" ; then returnCode=99 ; fi


	return $returnCode ;
}

function build_auto_play_file() {

	local targetFolder=${1:-$(pwd)};

	append_file "# generated " "csap-auto-play.yaml"

}

function append_yaml_comment() {
	local comment=${1:-} ;
	append_line "# $comment" ;
}

function append_yaml() {
	local numIndents=${1:-} ;
	local line=${2:-} ;

	local spaces='' ;
	if (( $numIndents > 0 )) ; then
		spaces=$(printf '  %.0s' {1..$numIndents}) ;
	fi ;

	append_line "$spaces$line" ;
}


function append_line() {
	append_file "$*"
}

lastTargetFile="no-file-specified-yet"
lastVerbose=true ;

function append_file() {

	local source="$1" ;
	local targetFile="${2:-$lastTargetFile}" ;
	local verbose="${3:-$lastVerbose}" ;

	if [[ "$targetFile" == "" ]] || [[ "$targetFile" == "no-file-specified-yet" ]] ; then
		print_error "Invalid target file '$targetFile' "
		return 99;
	fi ;

	if [[ "$verbose" == "" ]] ; then verbose=true ; fi ;
	lastTargetFile="$targetFile" ;
	lastVerbose=$verbose ;

	if ! test -f $targetFile ; then
		print_if_verbose $verbose "append_file" "Note: specified targetFile '$targetFile', does not exist, creating" ;
	fi ;

	if test -f "$source" ; then

		print_if_verbose $verbose "append_file()" "file '$source' to file: '$targetFile'" ;
		cat $source >> $targetFile ;

	else

		print_if_verbose $verbose "append_file() " "line: '$source' to file: '$targetFile'" ;
		echo -e "$source" >> $targetFile
	fi ;
}

function addFilePrefix() {
  local fullFilePath=$1
  local prefix=${2:-trimmed}
  echo "$(dirname $fullFilePath)/${prefix}-$(basename $fullFilePath)"
}


function delete_all_in_file() {


	local searchString="${1:-no_string_specified}" ;
	local targetFile="${2:-$lastTargetFile}" ;
	local verbose="${3:-$lastVerbose}" ;

	if [[ "$targetFile" == "" ]] || [[ "$targetFile" == "no-file-specified-yet" ]] ; then
		print_error "Invalid target file '$targetFile' "
		return 99;
	fi ;

	if  ! test -f "$targetFile" ; then
		print_error "delete_all_in_file: specified file does not exist: '$targetFile'" ;
		return 99 ;
	fi ;


	if [[ "$searchString" == *\|* ]] ; then
		print_error "delete_all_in_file: specified strings contain '|' which is used as a delimiter" ;
		return 99 ;
	fi

	lastTargetFile=$targetFile ;

	local numOccurences=$(grep -o "$searchString" $targetFile | wc -l)

	if (( $numOccurences > 0 )) ; then

		print_if_verbose $verbose "delete_all_in_file" "Deleting $numOccurences lines containing '$searchString' in '$targetFile'" ;
		sed --in-place "\|$searchString|d" $targetFile ;
		#sed --in-place "/$searchString/d" $targetFile ;


	else
		print_if_verbose $verbose "delete_all_in_file" "WARNING: no occurences of '$searchString' in '$targetFile'" ;
	fi ;

}


csapReportTrimToken="___STARTING_RUN___" # MUST update dockerfiles if used there

function trimmed_text() {
  local theText="$*" ;

  if is_word_in_text $csapReportTrimToken "$theText" && (( $( echo "$theText" | wc -l  ) > 1 )); then
    echo "$theText" | sed "0,/$csapReportTrimToken/d"  ;
  else
    echo "$theText"
  fi ;

}

function find_word_after() {

  local theStringBefore="$1" ;
  shift 1 ;
  local theTextToSearch="$*" ;

  local trimmedText=$( trimmed_text "$theTextToSearch" ) ;
  if is_word_in_text $theStringBefore "$trimmedText" ; then
    # NOTE: DO NOT QUOTE trimmedText - this strips newlines so that the cut works in sed
    echo  $trimmedText | sed "s/.*$theStringBefore //" | cut -d " " -f 1 ;
  else
    echo "" ;
  fi ;

}

function sum_stream() {
  awk '{s+=$1} END {print s}'
}

function find_word_equals_number() {

  local theStringBefore="$1" ;
  shift 1 ;
  local theTextToSearch="$*" ;

  echo $( trimmed_text "$theTextToSearch" ) |  grep -o "\b$theStringBefore=[(0-9)\.]*" | cut -d "=" -f 2


}
function find_word_equals_string() {

  local theStringBefore="$1" ;
  shift 1 ;
  local theTextToSearch="$*" ;

  echo $( trimmed_text "$theTextToSearch" ) |  grep -o "\b$theStringBefore=[[:alnum:]\.]*" | cut -d "=" -f 2

}

function is_word_in_text() {

  local theStringBefore="$1" ;
  shift 1 ;
  local theTextToSearch="$*" ;

  if echo $theTextToSearch | grep $theStringBefore &> /dev/null ; then
    return 0 ;
  else
    return 1 ;
  fi

}

function formatLine() {

  local columnWidth="$1" ;
  shift 1 ;
  local theText="$*" ;

  for word in $(echo $theText) ; do
    printf "%${columnWidth}s " $word ;
  done ;

  printf "\n"
}




function comma_separate() {

	local original="$*";

	echo ${original// /,} ;
}



function to_lower_hyphen() {
	local original=$( to_lower_case "$*" );
	echo ${original//[^a-z]/-} ;
}


function to_lower_case() {
	echo "$1" | tr '[:upper:]' '[:lower:]'
}


function add_after_match_in_file() {

	local searchString="$1" ;
	local replaceString="$2" ;
	local targetFile="${3:-$lastTargetFile}" ;
	local verbose="${4:-$lastVerbose}" ;

	if [[ "$verbose" == "" ]] ; then verbose=true ; fi ;
	lastTargetFile=$targetFile ;
	lastVerbose=$verbose ;

	if [[ "$searchString" == *\|* ]] || [[ "$replaceString" == *\|* ]] ; then
		print_with_head "ERROR: replace_all_in_file: specified strings contain '|' which is used as a delimiter" ;
		return 99 ;
	fi

	if ! test -f $targetFile ; then
		print_with_head "ERROR: replace_all_in_file: specified file does not exist: '$targetFile'" ;
		return 99 ;
	fi ;

	local numOccurences=$(grep -o "$searchString" $targetFile | wc -l)

	if (( $numOccurences == 1 )) ; then

		local replaceSafeForOutput="${replaceString//$'\n'/\\n}" ;
		replaceSafeForOutput="${replaceSafeForOutput}"
		print_if_verbose $verbose "add_after_match_in_file" "Found $numOccurences occurences of '$searchString', adding '$replaceSafeForOutput' in '$targetFile'" ;
		sed --in-place "/$searchString/a\\\n${replaceSafeForOutput}" $targetFile ;

	else
		print_if_verbose $verbose "replace_all_in_file" "WARNING: $numOccurences occurences of '$searchString' in '$targetFile'" ;
	fi ;
}

function replace_first_match() {

	local searchString="$1" ;
	local replaceString="$2" ;
	local targetFile="${3:-$lastTargetFile}" ;

  sed --in-place "0,/$searchString/s//$replaceString/" $targetFile
}

function replace_line_match_in_file() {

	local searchString="$1" ;
	local replaceString="$2" ;
	local targetFile="${3:-$lastTargetFile}" ;
	local verbose="${4:-$lastVerbose}" ;

	if [[ "$verbose" == "" ]] ; then verbose=true ; fi ;
	lastTargetFile=$targetFile ;
	lastVerbose=$verbose ;

	if [[ "$searchString" == *\|* ]] || [[ "$replaceString" == *\|* ]] ; then
		print_with_head "ERROR: replace_all_in_file: specified strings contain '|' which is used as a delimiter" ;
		return 99 ;
	fi

	if ! test -f $targetFile ; then
		print_with_head "ERROR: replace_all_in_file: specified file does not exist: '$targetFile'" ;
		return 99 ;
	fi ;

	local numOccurences=$(grep -o "$searchString" $targetFile | wc -l)

	if (( $numOccurences == 1 )) ; then

		local replaceSafeForOutput="${replaceString//$'\n'/\\n}" ;
		replaceSafeForOutput="${replaceSafeForOutput}"
		print_if_verbose $verbose "replace_line_match_in_file" "Found $numOccurences occurences of '$searchString', adding '$replaceSafeForOutput' in '$targetFile'" ;
		sed --in-place "s|.*${searchString}.*|${replaceSafeForOutput}|g" $targetFile ;

	else
		print_if_verbose $verbose "replace_all_in_file" "WARNING: $numOccurences occurences of '$searchString' in '$targetFile'" ;
	fi ;
}


function replace_all_in_file() {

	local searchString="$1" ;
	local replaceString="$2" ;
	local targetFile="${3:-$lastTargetFile}" ;
	local verbose="${4:-$lastVerbose}" ;

	if [[ "$verbose" == "" ]] ; then verbose=true ; fi ;
	lastTargetFile=$targetFile ;
	lastVerbose=$verbose ;


	if [[ "$searchString" == *\|* ]] || [[ "$replaceString" == *\|* ]] ; then
		print_with_head "ERROR: replace_all_in_file: specified strings contain '|' which is used as a delimiter" ;
		return 99 ;
	fi

	if ! test -f $targetFile ; then
		print_with_head "ERROR: replace_all_in_file: specified file does not exist: '$targetFile'" ;
		return 99 ;
	fi ;

	local numOccurences=$(grep -o "$searchString" $targetFile | wc -l)

	if (( $numOccurences > 0 )) ; then

		local replaceSafeForOutput="$replaceString" ;
		if [[ $(to_lower_case $searchString) == *"pass"* ]] ; then replaceSafeForOutput='*MASKED*' ; fi;
		print_if_verbose $verbose "replace_all_in_file" "Replacing $numOccurences occurences of '$searchString' with '$replaceSafeForOutput' in '$targetFile'" ;
		sed --in-place "s|$searchString|$replaceString|g" $targetFile ;

	else
		print_if_verbose $verbose "replace_all_in_file" "WARNING: no occurences of '$searchString' in '$targetFile'" ;
	fi ;

}


function keepLatestFiles() {

    local location=${1:-/missing} ;
    local numberToKeep=${2:-100} ;
    local pattern=${3:-.*} ;
    local previewMode=${4:-isPreview} ;
    local useRoot=${5:-isNotRoot} ;

    print_separator "keepLatestFiles: removes filesystem items" ;
    print_two_columns "location" "$location" ;
    print_two_columns "numberToKeep" "$numberToKeep" ;
    print_two_columns "pattern" "$pattern" ;
    print_two_columns "previewMode" "$previewMode" ;
    print_two_columns "useRoot" "$useRoot" ;

    local folderItems=$(ls -td $location/* | grep "${pattern}" | sed -e "1,${numberToKeep}d")

    for folderItem in $(echo $folderItems) ; do

      # run_using_root rm -rf $folderItem

      if [[ "$previewMode" == "isPreview" ]] ; then
        print_two_columns "delete preview" "$(ls -ld $folderItem)" ;
      else
        if [[ "$useRoot" == "isNotRoot" ]] ; then
          rm --verbose --recursive --force $folderItem
        else
          run_using_root rm --recursive --force $folderItem
        fi

      fi ;
    done
}

function backup_file() {

	local originalFile=${1:-no-backup-file}
	local optionalBackupFolder=${2:-no-backup-folder};

	local backupFile="$originalFile.last" ;
	if [ "$optionalBackupFolder" != "no-backup-folder" ] ; then
		backupFile="$optionalBackupFolder/$( basename $backupFile )" ;
	fi ;

	if test -r "$originalFile"  ; then
		print_if_verbose  true "backup_file" "Backing up $originalFile to $backupFile"

		if test -r "$backupFile" ; then
			print_if_verbose  true "backup_file" "Removing previous backup" ;
			rm --recursive --force $backupFile
		fi ;

		mv --force	--verbose $originalFile $backupFile;

	fi ;

}

function backup_folder() {

	local originalFolder=${1:-missing-folder} ;
	local useRoot=${2:-no} ;
	local useDate=${3:-no} ;
	local maxBackupCount=${4:-50} ;
	local prevFolderName=${5:-$(basename $originalFolder)} ;

  local NOW=$(date +"%h-%d--%H-%M-%S")

	local backupFolder="$originalFolder.orig" ;


	local commandRunner="run_and_format"
	if [[ "$useRoot" == "useRoot" ]] ; then
	  commandRunner="run_and_format_root"
	fi ;

	if test -d $backupFolder ; then
		backupFolder="$originalFolder.last"  ;
		if [[ "$useDate" == "useDate" ]] ; then
		  backupFolder="$originalFolder-previous/${prevFolderName}--$NOW"  ;
		  $commandRunner mkdir --parents $(dirname $backupFolder)  2>&1
		  # prune older items
		  local backupCount=$( ls -ld  $originalFolder.*-* | wc -l )

      if (( $backupCount > $maxBackupCount )) ; then
        print_two_columns "folder" "$originalFolder"
        print_two_columns "backupCount" "$backupCount"
        print_two_columns "maxBackupCount" "$maxBackupCount"
        local numToDelete=$(( $backupCount - $maxBackupCount ))
        run_and_format_root "ls -td $originalFolder.*-* | tail -$numToDelete | xargs \rm  --verbose --recursive --force" ;
      fi ;

    fi ;
	fi ;


	if test -d "$originalFolder"  ; then

		if test -d "$backupFolder" ; then
		  print_two_columns "removing" "$backupFolder"
			$commandRunner rm --recursive --force $backupFolder 2>&1
		fi ;

    $commandRunner mv --force	--verbose $originalFolder $backupFolder 2>&1 ;

  else
    print_error " backup_folder: '$originalFolder' does not exist"
	fi ;

}

function backup_original() {

	local originalFileOrFolder="$1" ;
	local useRoot=${2:-no} ;

	local newLocation="$originalFileOrFolder.original" ;
	if test -e $newLocation ; then
		newLocation="$originalFileOrFolder.last"  ;
	fi ;


	local commandRunner="run_and_format"
	if [[ "$useRoot" == "useRoot" ]] ; then
	  commandRunner="run_and_format_root"
	fi ;


	if test -r "$originalFileOrFolder"  ; then
		print_if_verbose  true "backup_original" "Backing up $originalFileOrFolder to $newLocation"

		if test -r "$newLocation" ; then
			print_if_verbose  true "backup_original" "Removing previous backup" ;
			$commandRunner rm --recursive --force $newLocation
		fi ;

		$commandRunner cp --force --recursive $originalFileOrFolder $newLocation;

	fi ;

}

function backup_and_replace() {

	local originalFile="$1" ;
	local updatedFile="$2" ;

	print_line "Updating $originalFile with $updatedFile. $originalFile is being backed up"

	if test -f $originalFile ; then
		if ! test -f $originalFile.orig ; then
			mv 	$originalFile $originalFile.orig;
		else
			mv 	$originalFile $originalFile.last;
		fi ;
	else
		originalFolder=$( dirname $originalFile )
		if ! test -e "$originalFolder" ; then
			print_line "Did not find $originalFolder, creating."
			mkdir -p "$originalFolder"
		fi ;
	fi

	cp --verbose --force $updatedFile $originalFile

}

function buildGrepPatternFromArray() {

	local patterns=("$@");
	local grepPatternString="";
	local pattern="" ;
  for patternWithQuote in "${patterns[@]}" ; do
    pattern=$(echo "$patternWithQuote" | tr -d '"')
    grepPatternString="$grepPatternString --regexp \"$pattern\""
  done ;

  echo $grepPatternString ;
}

function printAndCheckForExit() {
  local lineRead="$*"
  echo "$lineRead"
  if [[ "$lineRead" =~ $exitOnMatch ]] ; then
    # echo "Found somecompany3tatsReporter , exiting parent '$PPID'"
    # echo "location: $location"
    pidLineToKill=$(ps -ef | grep "tail -F" | grep -v grep | grep -v timeout | grep "$location" );

    if (( $(echo "$pidLineToKill" | wc -w ) > 2 )) ; then
      # echo "pidLineToKill: '$pidLineToKill'"
      kill -9 $(echo $pidLineToKill | awk '{ print $2 }' )
    fi;

  fi ;
}

function tail_print_and_exit() {

	local location="${1}";
	local exitOnMatch="${2}";
	local timeoutMinutes="${3}";
	shift;shift;shift;
	local searchPatterns=("$@");
	local grepOptions="";
  if [[ "$location" != "" ]] && test -f "$location" ; then

    searchPatternAsGrepRegEx=$(buildGrepPatternFromArray "${searchPatterns[@]}" )
    grepOptions="--no-group-separator" ;
    linesBeforeMatch=1
    linesAfterMatch=1
    initialTailLines=$(wc -l < $location)


    print_two_columns "search" "$searchPatternAsGrepRegEx"
    print_two_columns "exitOnMatch" "$exitOnMatch"
    print_two_columns "location" "$location"
    print_two_columns "initialTailLines" "$initialTailLines"
    print_two_columns "linesBeforeMatch" "$linesBeforeMatch"
    print_two_columns "linesAfterMatch" "$linesAfterMatch"
    print_two_columns "grepOptions" "$grepOptions"




    export -f printAndCheckForExit
    export location
    export exitOnMatch

    print_separator "output"
    timeout "$timeoutMinutes" tail -F $location --lines=$initialTailLines \
      | eval grep  \
      --line-buffered \
      --after-context=$linesAfterMatch \
      --before-context=$linesBeforeMatch \
      $grepOptions $searchPatternAsGrepRegEx \
      | xargs -d '\n' -n1 bash -c 'printAndCheckForExit "$@"' arg_0_for_function
  else
    print_error "Location not found: '$location'"
  fi;
}


function wait_for_passed() {


  local max_poll_result_attempts=${1:-10} ;
  shift
  local commandToRun="$*"

  local currentAttempt=1
  local attemptCount

  #
  # allow error commands for the run (default is disabled in kube jenkins)
  #
  set +e
  for attemptCount in $(seq $currentAttempt $max_poll_result_attempts); do

    # run command and swallow output - final output will be shown below

    print_section "wait_for_passed() $attemptCount of $max_poll_result_attempts ==>   $commandToRun"
    eval $commandToRun  >/dev/null
    local returnCode="$?"
    print_section "returnCode: $returnCode"
    if (( $returnCode == 0 )) ; then
      break ;
    else
      sleep 1 ;
    fi ;

  done
  set -e

  #
  # run one final time to exit if needed
  #
  eval $commandToRun
}



function wait_for_command_output() {

  local logPattern="${1:-patternNotSpecified}"
  local command=${2:-not-specified}
  local max_poll_result_attempts=${3:-50} ;
  local sleepSeconds=${4:-10} ;
  local exitOnFailure=${5:-false} ;
  local currentSeconds=$sleepSeconds ;

  local startSeconds=$(date +%s) ;
  local endSeconds=$startSeconds ;
  local runtime=$startSeconds ;
  local formatedTime=$startSeconds ;

  print_with_head "checking every $sleepSeconds seconds for pattern: '$logPattern' in command: '$command'"

  local currentAttempt=1;
  local logMatches=0;

  for attemptCount in $(seq $currentAttempt $max_poll_result_attempts); do


    endSeconds=$(date +%s) ;
    runtime=$(( $endSeconds - $startSeconds)) ;

    formatedTime=$(date -d@$runtime -u "+%Mm %Ss") ;



    print_line "attempt $attemptCount of $max_poll_result_attempts ($formatedTime):  waiting for pattern: '$logPattern'\n"
    # logMatches=$(grep "$logPattern" "$(eval command)" | wc -l) ;

    #run_and_format $command

    logMatches=$( echo "$(eval $command)" | grep "$logPattern" | grep -v grep | wc -l) ;
    if (( $logMatches > 0 )) ; then
      break ;
    else
      sleep $currentSeconds;
    fi ;


    if (( $attemptCount > 20 )) ; then
      currentSeconds=$(( $sleepSeconds * 6 )) ;
    elif (( $attemptCount > 10 )) ; then
      currentSeconds=$(( $sleepSeconds * 3 )) ;
    elif  (( $attemptCount > 5 )) ; then
      currentSeconds=$(( $sleepSeconds * 2 )) ;
    fi ;
  done

  print_with_head "Matches Found: '$logMatches' for pattern: '$logPattern',  in command: '$command' "

  if [[ "$exitOnFailure" == "exitOnFailure" ]] ; then
    if (( $logMatches == 0 )) ; then
      print_error "wait_for_command_output: failed to find match, exiting" ;
      exit 9876 ;
    fi ;
  fi ;



}





function wait_for_log() {

	local logPattern="${1:-patternNotSpecified}"
	local logFile=${2:-/file/not/specified}
	local max_poll_result_attempts=${3:-50} ;
	local sleepSeconds=${4:-10} ;
	local currentSeconds=$sleepSeconds ;

	local startSeconds=$(date +%s) ;
	local endSeconds=$startSeconds ;
	local runtime=$startSeconds ;
	local formatedTime=$startSeconds ;

	print_with_head "checking every $sleepSeconds seconds for pattern: '$logPattern' in logFile: '$logFile'"

	local currentAttempt=1;
	local logMatches=0;

	for attemptCount in $(seq $currentAttempt $max_poll_result_attempts); do

			sleep $currentSeconds;

			endSeconds=$(date +%s) ;
			runtime=$(( $endSeconds - $startSeconds)) ;

			formatedTime=$(date -d@$runtime -u "+%Mm %Ss") ;


			if test -f "$logFile" ; then

				print_line "attempt $attemptCount of $max_poll_result_attempts ($formatedTime):  waiting for pattern: '$logPattern'\n"
				logMatches=$(grep "$logPattern" "$logFile" | wc -l) ;
				if (( $logMatches > 0 )) ; then
					break ;
				fi ;
			else
				print_line "attempt $attemptCount of $max_poll_result_attempts ($formatedTime ):  waiting for file to exist \n"
			fi ;

		if (( $attemptCount > 20 )) ; then
      currentSeconds=$(( $sleepSeconds * 6 )) ;
    elif (( $attemptCount > 10 )) ; then
			currentSeconds=$(( $sleepSeconds * 3 )) ;
		elif  (( $attemptCount > 5 )) ; then
			currentSeconds=$(( $sleepSeconds * 2 )) ;
		fi ;
	done

	print_with_head "Matches Found: '$logMatches' for logs pattern: '$logPattern',  in logFile: '$logFile' "

}

function wait_for_file() {

	local logFile=${1:-/file/not/specified}
	local max_poll_result_attempts=${2:-30} ;
	local sleepSeconds=${3:-2} ;

	print_with_head "checking every $sleepSeconds seconds for file: '$logFile'"

	local currentAttempt=1;
	local foundFile=false;

	for i in $(seq $currentAttempt $max_poll_result_attempts); do

			sleep $sleepSeconds;

			print_line "$(( $i * $sleepSeconds ))s - attempt $i of $max_poll_result_attempts:  waiting for file: '$logFile'\n"

			if test -f $logFile ; then
				foundFile=true ;
				break ;
			fi ;
	done

	print_with_head "File: '$logFile' foundFile: '$foundFile' "

}

function launch_background () {

	command="$1" ;
	arguments="$2" ;
	logFile="$3" ;
	appendLog="${4:-no}" ;
	useRoot="${5:-no}" ;


	print_section "Launch Background: '$command'"
	print_two_columns "pwd" "$(pwd)"
	print_two_columns "useRoot" "$useRoot"
	print_two_columns "logs" "location '$logFile', append: '$appendLog'"
	print_two_columns "Arguments" "$arguments"

	# First spawn a background process to do the agent kill
	# redirect error: 2>&1  replace file if exists and noclobber set: >|

	local runRoot="" ;
	if [ "$useRoot" == "yes" ] ; then runRoot="run_using_root" ; fi

	if [ "$appendLog" == "appendLogs" ] ; then

		$runRoot nohup $command $arguments >> $logFile 2>&1 &

	else

		backup_file $logFile $csapSavedFolder
		if test -f $logFile ; then
			rm --force $logFile ;
		fi
		$runRoot nohup $command $arguments > $logFile 2>&1 &

	fi

	thePid="$!" ; theReturnCode=$? ;
	thePidFile="${thePidFile%.log}.pid"
	if test -f $thePidFile ; then
		rm --force $thePidFile ;
	fi

	echo $thePid > $thePidFile
	print_two_columns "return code" "$theReturnCode"
	print_two_columns "pidFile" "$thePidFile"

	# sleep is need to making sure any errors output by the nohup itself are captured in output
	sleep 1 ;

}

function getPropertyValueFromFile() {

	local propertyKey="${1:-property-not-specified}" ;
	local propertyFile="${2:-/file/not/specified}" ;
	local propertyValue=$(grep "$propertyKey" $propertyFile | awk -F "=" '{print $2}') ;

	echo "$propertyValue";
}

function add_link_in_pwd() {

	local pathOnOs=${1:-/path/not/specified} ;

	local shortPath=${2:-yes} ;

	local linkedPath="link-to-"${pathOnOs////-} ;

	if [ "$shortPath" == "yes" ] ; then
		linkedPath="link-to-$(basename $pathOnOs)"
	fi ;

	print_two_columns "$pathOnOs" "linked: $linkedPath"

	ln -s $pathOnOs $linkedPath
}


note_indent="   ";
function add_note() {

	currentNote="$*" ;
	if [ "$currentNote" == "start" ] ; then
		add_note_contents="\n$LINE\n" ;

	elif [ "$currentNote" == "end" ] ; then
		add_note_contents+="\n$LINE\n" ;

	else
		add_note_contents+="$currentNote\n$my_indent"
	fi ;
}

#
#   Helpers
#

#
# bat setup (formatted cat)
#
batLightTheme="gruvbox-light"
batDarkTheme="gruvbox-dark"
batTheme="$batLightTheme"

alias blog="bbat java --theme=$batTheme --paging=always"

# iterm theme detection
case "$ITERM_PROFILE" in

  dark | *ht | *_ )
    batTheme="${batDarkTheme}"
    ;;
esac
#if test -n "$FIG_JETBRAINS_SHELL_INTEGRATION" ; then
if test -n "$TERMINAL_EMULATOR" ; then
  echo "intellij light theme applied by misc.sh"
  batTheme="${batLightTheme}" ;
fi

batDefaultParameters="--theme=$batTheme --language=bash --paging=never"


function btail() {

	local file=${1};
	local lineCount=${2:-500}
	local type=${3:-java}

	ff
	print_section "Tailing $file (ctrl-c to quit)"
	tail -n $lineCount -F $file | bbat $type --theme=$batTheme --paging=never

}


alias batt="catt"
function catt() {

	local filepath=${1:-missingFile};
	local type=${2:-};

	local filename=$(basename -- "$filepath") ;

	if ! test -f $filepath ; then
		print_section "parameter 1 is not a file: $filepath"  ;
		return ;
	fi ;

	if [[ "$type" == "" ]] && [[ "$filename" != *'.'* ]]; then
		type="bash" ;
	fi ;

	local extension="${filename##*.}"
	if [[ "$extension" == "local" ]] ; then
		type="bash" ;
	fi ;

	clear ;
	print_separator "'$filepath' '$type'"

	if [[ "$type" != "" ]] ; then
		#highlight --syntax=$type --stdout "$filepath" ;
		bat --theme="$batTheme" --paging=never --language=$type "$filepath"
	else
		#highlight --stdout "$filepath" ;
		bat --theme="$batTheme" --paging=never "$filepath"
	fi ;

}


alias battf="cattt"
function cattf() {

	local filepath=${1:-/etc/hosts};
	local search=${2:-aws};
	local before=${3:-3};
	local after=${4:-3};
	local type=${5:-};

	clear ;

	print_separator "file: '$filepath' search '$search' lines before: $before after: $after type: $type"
	# cat $filepath | grep $search --before-context $before --after-context $after


	if [[ "$type" == "" ]] && [[ "$filename" != *'.'* ]]; then
		type="bash" ;
	fi ;

	if [[ "$type" != "" ]] ; then
		#highlight --syntax=$type --stdout "$filepath" ;
		cat $filepath | grep $search --before-context $before --after-context $after | bat --language=$type --theme="$batTheme"
	else
		#highlight --stdout "$filepath" ;
		cat $filepath | grep $search --before-context $before --after-context $after | bat --theme="$batTheme"
	fi ;

	# run_and_format "cat $filepath | grep $search --before-context $before --after-context $after | bat --theme=gruvbox-light"
	# cat $filepath | grep $search --before-context $before --after-context $after | bat --language=$type --theme=gruvbox-light



}


alias ff="bbat"
#alias fformatAsSh="bbat bash --paging=never"
alias ffyaml="bbat yaml"
alias ffjava="bbat java"
alias ffgradle="bbat gradle"

function bbat() {
	local formatLanguage=${1:-bash};
	local moreParams="$*";
	if (( $# > 1 )) ; then
		shift ;
		moreParams="$*";
		eval bat --theme="$batTheme" --language=$formatLanguage --paging=never $moreParams
	else
	  eval bat --theme="$batTheme" --paging=never $moreParams
	fi

	# if [[ "$type" == "json" ]] ; then
	# 	moreParams+="--theme=OneHalfLight"
	# fi


	#print_two_columns "type" "'$type'"
	#print_two_columns "moreParams" "'$moreParams'"

}

function pssjava() {
  pss java
}


function pkjava() {
  pss java

  if ! confirmation_prompt "Proceed with 'killall -9 java' ?" ; then
    print_subsection "Clean Skipped"
    return ;
  fi ;

  run_and_format killall -9 java

  pss java
}

function grepExclude() {
  grep --invert-match --word-regexp "$@"
}

function pss() {

  local isRemote=false
	if [[ "$1" == "suv" ]] ; then
	  isRemote=true
	  shift
	fi
	local filter=${1:-};
	local cutLength=${2:-100};
	local paging=${3:-never};
	local splitParams=${4:-split};
	local psOutput=${5:-none}
	if $isRemote ; then
	  print_two_columns "remote collection" "'$suvHost'"
	  psOutput="$(ssh root@$suvHost "ps -ef" 2>/dev/null)"
	fi

	print_two_columns "filter" "'$filter'"
	print_two_columns "cutLength" "'$cutLength'"
	print_two_columns "paging" "'$paging' eg. pss java always | never"
	print_two_columns "splitParams" "'$splitParams'"

	if [[ "$psOutput" == "none" ]] ; then
	  psOutput="$(ps -ef)"
	fi

	if [[ "$filter" == "" ]] ; then
		echo "$psOutput" | cut --characters 1-$cutLength | bbat awk --paging=always

	else
		if [[ "$splitParams" != "split" ]] ; then
			echo "$psOutput" | grep -E -- "$filter|STIME   TTY" \
				| grep -v grep | sed 's/^/\n\n/' \
				| trimAndShow $cutLength  \
				| bbat awk --paging=${paging}

		elif [[ "$filter" == "myapp" ]] ; then
			echo "$psOutput" | grep -e "STIME   TTY" -e "zookeeper" -e "mysqld" -e "java" \
        | _pssEval $cutLength

		else
			echo "$psOutput" | grep -E -- "$filter|STIME   TTY" \
				| _pssEval $cutLength
		fi
	fi
}

function _pssEval() {

  local cutLength=${1:-999} ; shift 1;
  local expression="$*"

  cat \
    | grep -v grep \
    | sed 's/^/\n\n/' | sed 's/-X/\n\t -X/g' | sed 's/--/\n\t --/g' | sed 's/-D/\n\t -D/g' \
    | sed 's/-classpath/\n\t -classpath/g' | sed 's/-cp/\n\t -cp/g' \
    | trimAndShow $cutLength  \
    | bbat awk --paging=${paging}

}

function trimAndShow() {
  local maxLength=${1:-999} ; shift 1;
  local expression="$*"

  if test -n "$1"; then
    #param passed
    _trimEval $maxLength $expression
  elif test ! -t 0; then
    # stdIn
    while read expression; do
      _trimEval $maxLength $expression
    done ;
  else
      echo "No standard input."
  fi
}

function _trimEval() {
  local maxLength=${1:-999} ; shift 1;
  local expression="$*"

  if (( "${#expression}" > "$maxLength" )) ; then
    local firstWord="${expression%% *}"
    if ! [[ "$firstWord" =~ ^[0-9]+$ ]] ; then
      echo "${expression:0:$maxLength} ..."
    else
      echo "$expression"
    fi

  else
    echo "$expression"
  fi
}


function lss() {

	local filter=${1:-};

	print_two_columns "filter" "'$filter'"

	if [[ "$filter" == "" ]] ; then
		ls -al | bbat js --theme=$batTheme --paging=always

	else
		ls -al | grep $filter | sed 's/^/\n\n/' | bbat js --theme=$batTheme --paging=always
	fi
}

function findInJava() {

	local filter="${1:-someJavaString}";
	local folder="${2:-.}";

	print_two_columns "filter" "'$filter'"
	print_two_columns "folder" "'$folder'"

  find $folder -name "*.java" -exec grep -e "$filter" "{}" \;
}

function grepType() {

	local suffix="${1:-java}";
	local filter="${2:-someString}";
	local folder="${3:-.}";

	print_two_columns "filter" "'$filter'"
	print_two_columns "file type" "'$suffix'"
	print_two_columns "folder" "'$folder'"

  #find $folder -name "*.$suffix" -exec grep -e "$filter" "{}" \;
  grep --recursive --initial-tab --include "*.$suffix" --extended-regexp "$filter" $folder
}

function findd() {

	local filter="$1";
	local folder="${2:-.}";
	local matchLength=${3:-30}


	if test -f "$folder" ; then
	  cattf "$folder" "$filter"
	  return ;
	fi ;


	grep --recursive \
		--only-matching \
		--initial-tab \
		--extended-regexp \
		".{0,$matchLength}${filter}.{0,$matchLength}" $folder \
		| sed 's/:/:   /' \
		| awk -v i=1 'NR>1 && $i!=p { print "---\n" }{ p=$i } 1' \
		| grep -e "--" -e "$filter"
		# | bbat yaml --theme=$batTheme --paging=always

	# local params="$*";

	# linesBefore=0
	# linesAfter=0

	# print_two_columns "params" "'$params'"


	# 	# --initial-tab \
	#   # --after-context=$linesAfter \
  # 	# --before-context=$linesBefore \

	# grep \
  # 	$params \
  # 	| sed 's/:/\n/g'
  #  	# | bbat sh --theme=$batTheme --paging=always

}


function path() {

	echo $PATH | sed 's/:/\n/g' | bbat sh --theme=$batTheme --paging=always

}

function lsoff() {

	local filter=${1:-7197};
	shift;

	local batParams="$*";

	local filterCommand="| grep $filter" ;

	clear ;

	print_separator "lsof -i -n -P | grep TCP $filterCommand  $batParams"

	if [[ "$filter" == "none" ]] ; then
		lsof -i -n -P | grep TCP | bbat java $batParams

	else
	 lsof -i -n -P | grep TCP | grep $filter | bbat java $batParams
	fi ;




}

function sss() {


	clear ;

	print_section "ps -ef| grep -v  -e grep -e ssh-agent| grep ssh"

	ps -ef| grep -v  -e grep -e ssh-agent| grep ssh

}


#
# Build the report. defaults to sort unique file before comparing
#     prefix=category-asIs | category-sortU | category-noIndents
#
function diffDepends() {
  local originalReport=$1
  local updatedReport=$2
  local reportPathPrefix=${3:-$(dirname $originalReport)/default-noIndents}

  local outputFile=${reportPathPrefix}.txt
  local originalFile=${reportPathPrefix}-SOURCE-original.txt
  local updatedFile=${reportPathPrefix}-SOURCE-updated.txt
  outputFile=$(addFilePrefix $outputFile "a-diff-with")
  local outputTmpFile=${reportPathPrefix}-z-diff-tmp.txt

  mkdir --parents $(dirname $reportPathPrefix)
  cp --verbose $originalReport $originalFile
  cp --verbose $updatedReport $updatedFile

  print_mainsection "Building diff Report"
  print_two_columns "originalReport" "$originalReport"
  print_two_columns "updatedReport" "$updatedReport"
#  print_two_columns "originalFile(working)" "$originalFile"
#  print_two_columns "updatedFile(working)" "$updatedFile"
  print_two_columns "outputFile" "$outputFile"

  local diffTypeAndLines=""
  local doTrim=false
  local doSort=false

  #  diffTypeAndLines="--unified=1"
  case "$reportPathPrefix" in

      *asIs ) diffTypeAndLines="" ;;

      *sortU )  doTrim=true; doSort=true ;;

      *noIndents | * ) doTrim=true ;;

  esac

  if $doTrim ; then
      #
      # Optional Strips out extra spaces, special characters, and overridden versions
      #   javax.validation:validationapi:1.0.0.GA > 2.0.1.Final
      #   => javax.validation:validationapi:2.0.1.Final
      #   | sed -E 's|(:.*):(.*? > )|\1:|' \

    cat $originalReport \
      | sed 's|[+\\]---||g'  | tr -d '|' | tr -d '\\' \
      | sed 's|  *| |g' \
      > $originalFile

    cat $updatedReport \
      | sed 's|[+\\]---||g' | tr -d '|' | tr -d '\\' \
      | sed 's|  *| |g' \
      > $updatedFile
  fi

  if $doSort ; then
    sort -u -o $originalFile $originalFile
    sort -u -o $updatedFile $updatedFile
  fi

#  print_two_columns "originalFile" "$originalFile" > $outputFile
#  print_two_columns "updatedFile" "$updatedFile" >> $outputFile
  print_two_columns "Base File" "$originalReport" > $outputFile
  print_two_columns "Updated File(*)" "$updatedReport" >> $outputFile

  diff --ignore-space-change $diffTypeAndLines \
    $originalFile \
    $updatedFile \
    >> $outputFile

  local returnCode=$?

  #
  # parse diff output to enhance readability
  #   note use of sed regex groups: \1 = first pattern, ...
  #
#  if $doTrim ; then
#    mv  $outputFile $outputTmpFile
#    cat $outputTmpFile \
#    | sed '/^---$/d' \
#    | sed -E 's/(^[0-9,]+)[ac]([0-9]+)/\n\n==> line number:     base file: \1     updated file*: \2/' \
#    | sed 's|^> |     |' | sed 's|^< |    *|' \
#    > $outputFile
#  fi


  return $returnCode

}


function is_gradle_build_error() {

  local dependsOutputFile=$1

  if does_file_contain_word $dependsOutputFile "FAILURE: Build failed" ; then
    print_line "    -- found 'FAILURE: Build failed' in output"

    if does_file_contain_word $dependsOutputFile "Compilation failed" ; then
      print_line "        -- found 'Compilation failed' in output"
    fi
    true
  else
    print_line "    -- no build errors detected"
    false
  fi

}

function is_gradle_heap_error() {

  local dependsOutputFile=$1

  # ANy failure: FAILURE: Build failed with an exception
  # java.lang.OutOfMemoryError
  if does_file_contain_word $dependsOutputFile "Exception in thread" ; then
    print_line "    -- found 'Exception in thread' in output"
    if does_file_contain_word $dependsOutputFile "java.lang.OutOfMemoryError" ; then
      delay_with_message 5 "WARNING: 'Build failed with an exception' in $(trimPath $dependsOutputFile 3)  "
      ggstop yes
      delay_with_message 10 "Retry build"
      true
    else
      false
    fi
    #ggg $buildFolder dependencies --quiet --configuration $config > $dependsOutputFile
  else
    false
  fi
}
