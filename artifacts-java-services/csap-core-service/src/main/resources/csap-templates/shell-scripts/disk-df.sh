# df , size of mounted volumes

# Notes:
# 1. Set directory as needed
# 2. If running against ROOT owned filesystem, switch user to root


requestedFolder="_file_"
cd $requestedFolder

hideErrors=true;


print_with_head "Running report for: '$requestedFolder'"

# run_using_root "du --summarize --human-readable --one-file-system * | sort --reverse --human-numeric-sort" ;

hideErrorOutput=""
if $hideErrors ; then
	hideErrorOutput="2> /dev/null" ;
fi ;

print_command \
	"disk usage estimate" \
	"$(root_command du --summarize --human-readable --one-file-system \*  $hideErrorOutput \| sort --reverse --human-numeric-sort)" ;
	

print_command \
	"df: inode usage" \
	"$(root_command df --inodes .)" ;
	

print_command \
	"df: report file system disk space usage" \
	"$(root_command df --human-readable --print-type)" ;
	

	

print_command \
	"Raw Disk via fdisk" \
	"$(root_command fdisk -l)"


print_command \
	"logical volume groups: vgdisplay" \
	"$(root_command vgdisplay)"


print_command \
	"logical volumes: lvs" \
	"$(root_command lvs)"


print_command \
	"logical volumes: lvscan" \
	"$(root_command lvscan)"




maxTimeToAvoidOsPainSeconds=10


print_with_head "Running file count report report with max timeout: $maxTimeToAvoidOsPainSeconds"

print_command \
	"file count with max timeout: $maxTimeToAvoidOsPainSeconds" \
	"$(root_command timeout $maxTimeToAvoidOsPainSeconds find . -type f | wc -l)" ;	

# note that this can cause a lot of system cpu and memory to be used. Run it on an idle vm and use csap os dashboard
# print_with_head "file counts by folder sorted with max timeout seconds: $maxTimeToAvoidOsPainSeconds"
# timeout $maxTimeToAvoidOsPainSeconds find . -xdev -printf '%h\n' | sort | uniq -c | sort -k 1 -n