# smem, physical memory usage taking shared memory pages into account


#
# https://www.baeldung.com/linux/process-memory-management
# ref https://linoxide.com/memory-usage-reporting-smem/
# 

pidsCommaSeparated="_pid_" ;
serviceName="_serviceName_" ;

if ! is_package_installed smem ; then

	print_two_columns "not installed" "run yum --assumeyes install smem" ;
	
	
	# run_using_root yum --assumeyes install smem
	
	exit ;
fi ;

print_command "smem" "$(run_using_root smem | grep --extended-regexp "Command|$pidsCommaSeparated" | grep -v -e grep )"


exit

print_command "smem --processfilter=java" "$(run_using_root smem --processfilter=java)"


print_command "smem --no-header --columns='pid pss rss' --processfilter=java" "$(run_using_root smem --no-header --columns='pid pss rss' --processfilter=java)"



print_command "smem --help" "$(smem --help)"


print_separator "all"
run_using_root time smem

delay_with_message 20 "delaying for test timing"
print_separator "java"
run_using_root time smem --processfilter=java

