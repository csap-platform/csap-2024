# processThreads, linux pstree output highlights the process child/parent relationships

pidsCommaSeparated="_pid_"
serviceName='_serviceName_' ;

# uncomment to run on multiple hosts
# pidsCommaSeparated=$(ps -eo pid,args | grep $serviceName | grep --invert-match --regexp grep | awk '{print $1}' | paste -d, -s -)


pidsSpaceSeparated="${pidsCommaSeparated//,/ }";
firstPid=${pidsSpaceSeparated%% *}
#print_line "parentPid: $parentPid pidsSpaceSeparated: $pidsSpaceSeparated firstPid: $firstPid"

print_command \
	"process parents" \
	"$(pstree -slp $firstPid | head -1)"


print_command \
	"process arguments" \
	"$(pstree -sla $firstPid )"
		
		
javaMatches=$(ps -ef | grep $pidsCommaSeparated | grep java | wc -l);


if (( $javaMatches >= 1 )) ; then
	
	processOwner=$(ps -o user= -p $pidsCommaSeparated) ;
	
	print_section   "processOwner '$processOwner' whoami '$(whoami)'"
	
	if [ "$processOwner" != "$(whoami)" ] ; then
		
	  	print_command \
	  		"java jstack as $processOwner" \
	  		"$(run_using_root su - $processOwner -c "'jstack -l $pidsCommaSeparated'")"
	
	else 
	
			if test -d "$csapPlatformWorking/$serviceName" ; then
				print_two_columns "creating report folder" "\$(mkdir --parent --verbose "$csapPlatformWorking/$serviceName" 2>&1)" ;
			fi ;

			stackOutputFile="$csapPlatformWorking/$serviceName/$serviceName-threads-$(date +'%b-%d--%H-%M').txt";
			$JAVA_HOME/bin/jstack -l $pidsCommaSeparated > $stackOutputFile
			
			
			fastThreadUrl="http://ycrashUrl"
			
			
			print_command \
				"Post  in file $stackOutputFile" \
				"$(curl -X POST --data-binary @$stackOutputFile $fastThreadUrl --header "Content-Type:text" )" ;
			
			print_command \
				"java jstack in file $stackOutputFile" \
				"$(cat $stackOutputFile )" ;
				
			
#		else 
#
#			print_command \
#				"java jstack" \
#				"$($JAVA_HOME/bin/jstack -l $pidsCommaSeparated)"
#		fi ;
	
	fi ;
	
	
	
	
	
else 


	print_command \
		"ps threads $(ps -Lf -p $pidsCommaSeparated | wc -l) total" \
		"$(ps -Lf -p $pidsCommaSeparated)"

	print_command \
		"ps threads -mo" \
		"$(ps -mo THREAD -p $pidsCommaSeparated)"
		

fi ;
