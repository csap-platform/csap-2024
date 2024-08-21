# journalctl , search linux journals for string


#
#  Used by Host Portal to search journal entries
#

service='_serviceName_' ;

filter="_searchText_" ; 



print_section "Using linux journalctl for service '$service'"


if [[ "$filter" != "no-search-text-param" ]] ; then
	
	run_using_root journalctl --no-pager \
		--since '"1 days ago"'  \
		--unit $service \
		| grep \
		--before-context=1 --after-context=3 \
		--fixed-strings "$filter"
	
else
	
	run_using_root journalctl --no-pager \
		--since "1 days ago"  \
		--unit $service
	
fi;

exit

# Run this for options
man journalctl | col -bx

# --reverse , --lines=100 , 