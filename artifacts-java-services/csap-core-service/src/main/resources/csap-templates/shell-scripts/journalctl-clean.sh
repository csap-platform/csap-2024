# journalctl , clean linux journals for disk space


#
#  ref https://unix.stackexchange.com/questions/130786/can-i-remove-files-in-var-log-journal-and-var-cache-abrt-di-usr
#


# clear the journal
# \rm --recursive --force /run/log/journal/* /var/log/journal/*
run_using_root journalctl --disk-usage

run_using_root journalctl --vacuum-size=500M

run_using_root journalctl --disk-usage