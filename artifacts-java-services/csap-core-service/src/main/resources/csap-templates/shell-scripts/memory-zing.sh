# zing-ps, configure zing pmem.conf file to allocate zing kernel memory settings

updatedPercent=80
contingencyPercent=2
pausePreventPercent=3
if (( $updatedPercent == 1 )) ; then
  contingencyPercent=0
  pausePreventPercent=0
fi

zingConfFile="/etc/zing/pmem.conf.0"
updatedConfFile=$(pwd)/updated-pmem.conf.0


run_and_format "print_separator current zing-ps -s -h ; zing-ps -s -h ; echo; print_separator current $zingConfFile ;  cat $zingConfFile ; "

sed "/Reservable/c\Reservable    ${updatedPercent}%" $zingConfFile > $updatedConfFile
sed --in-place "/Contingency/c\Contingency    ${contingencyPercent}%" $updatedConfFile
sed --in-place "/PausePrevention/c\PausePrevention    ${pausePreventPercent}%" $updatedConfFile

run_and_format "print_separator Updated $updatedConfFile;cat $updatedConfFile"

print_section "Comment out this line to proceed to apply changes" ; exit

run_and_format_root "touch /etc/zing/custom_pmem_config;backup_and_replace $zingConfFile $updatedConfFile"


#
# run twice to ensure reload
#
run_and_format_root "systemctl stop zing-memory 2>&1;print_line short delay to ensure reload; sleep 5;"
run_and_format_root "echo 1 > /proc/sys/vm/compact_memory ;  sleep 5;"
run_and_format_root 'print_line before:;free -h ; sync && echo 3 > /proc/sys/vm/drop_caches;print_line after:free -h;show_memory 10'

run_and_format_root "systemctl restart zing-memory 2>&1;print_line short delay to ensure reload; sleep 5;systemctl restart zing-memory 2>&1"

run_and_format_root 'journalctl --unit zing-memory --since "10 minutes ago";print_separator zing status; zing-ps -s -h'



