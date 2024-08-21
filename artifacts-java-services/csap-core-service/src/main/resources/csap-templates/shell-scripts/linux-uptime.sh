# uptime and df, Summary of host availability

runLocation="_file_"


if [[ "$runLocation" =~ \.jar$ ]] || [[ "$runLocation" =~ \.war$ ]] || [[ "$runLocation" =~ \.rpm$ ]] ; then
  show_java $runLocation

elif test -d $runLocation ; then
  cd $runLocation ;
fi




print_two_columns "host" "$(hostname)"

print_two_columns "user" "$(whoami)"

print_two_columns "linux uptime" "$(uptime | xargs)"

print_two_columns "os architecture" "$(printOsArchitecture)"

print_two_columns "os name" "$(printOsName)"


print_two_columns "java" "$(which java)"




if test -f "/etc/ssh/perf_banner" ; then
  run_and_format cat /etc/ssh/perf_banner
fi;











#
# csap cli examples
#



#curl \
#	--silent \
#	--request GET \
#	http://localhost:8011/api/agent/runtime \
#	| jq .hostStats


#curl \
#	--silent \
#	--data "userid=csap-performance-app&pass=csap-performance-app&content=demo&" \
#	--request POST \
#	"http://localhost:8011/api/application/autoplay"

#print_command \
#	"csap-agent host information" \
#	"$(agent agent/runtime)"
	
# print_two_columns "agent services" "$(agent model/services/name?reverse=true --parse | wc -w )"
# print_two_columns "csap services (all)" "$(csap model/services/name?reverse=true --parse | wc -w )"

# print_two_columns "agents up" "$(services_running csap-agent)"

# preflight, csap preflight function to asses state of system: source: click on files: bin/functions/misc.sh
# run_preflight  ;

