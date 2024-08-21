# processMemory, show either java memory (jmap) or os memory(pmap) information 


# updated automatically when launched from host dashboard 
pidsCommaSeparated="_pid_" ;
serviceName="_serviceName_" ;


function do_configuration() {
	
	#
	# Optional JDK defaults to openjdk17
	#
	# csapParams="-DzingJava17" ; source $CSAP_FOLDER/bin/functions/service.sh ; configure_java_environment ; java --version
	
	#
	# jmap alternative modes
	#
 	local collectTime=$(date +"%b-%d--%H-%M") ;
	outputFile="$csapPlatformWorking/$serviceName/logs/reports/$serviceName-jmap-$collectTime"
	histogramTextCommand="eval jmap -histo:live $pidsCommaSeparated > $outputFile.txt" ; # also -finalizerinfo , -clstats, 
	binaryDumpCommand="jmap -dump:live,format=b,file=$outputFile.hprof $pidsCommaSeparated" ; 
	
	jmapCommand="$histogramTextCommand"
	
	outputFolder=$(dirname $outputFile);
	print_two_columns "outputFolder" "$outputFolder"
	mkdir --parents --verbose $outputFolder; chmod 777 $outputFolder;
	
	# uncomment to run on multiple hosts
	# pidsCommaSeparated=$(ps -eo pid,args | grep $serviceName | grep --invert-match --regexp grep | awk '{print $1}' | paste -d, -s -)
	
	
	pidsSpaceSeparated="${pidsCommaSeparated//,/ }";
	firstPid=${pidsSpaceSeparated%% *}
	#print_line "parentPid: $parentPid pidsSpaceSeparated: $pidsSpaceSeparated firstPid: $firstPid"

	
}
do_configuration

function show_process_summary() {

	print_command \
		"process parents" \
		"$(pstree -slp $firstPid | head -1)"
	
	
	print_command \
		"process arguments" \
		"$(pstree -sla $firstPid )"
		
	
}
show_process_summary

function run_memory_dump() {
		

	javaMatches=$(ps -ef | grep $pidsCommaSeparated | grep java | wc -l);
	
	
	if (( $javaMatches >= 1 )) ; then
	
		processOwner=$(ps -o user= -p $pidsCommaSeparated) ;
		
		print_two_columns "processOwner" "'$processOwner' whoami '$(whoami)'"
		
		if [ "$processOwner" != "$(whoami)" ] ; then
			
		  	print_command \
		  		"jmap as $processOwner" \
		  		"$(run_using_root su - $processOwner -c "'$jmapCommand'")"
		
		else 
		
			
		
			print_command \
				"$jmapCommand" \
				"$( $jmapCommand )"
		
		fi ;
	
	
	else
		
		# ref. http://linoxide.com/linux-command/linux-memory-analysis-with-free-and-pmap-command/
		print_command \
			"Linux pmap for $pidsCommaSeparated. Change -d to -x to view rss" \
			"$( run_using_root pmap -x $pidsCommaSeparated )"
			
		
	
	fi ;
	
}

run_memory_dump
 