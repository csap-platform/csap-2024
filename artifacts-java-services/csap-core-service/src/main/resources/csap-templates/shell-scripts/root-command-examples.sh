# df , size of mounted volumes

# Notes:
# 1. Set directory as needed
# 2. If running against ROOT owned filesystem, switch user to root


requestedFolder="_file_"
cd $requestedFolder


print_section "root_csap_command"
root_csap_command "echo \$(id)"

print_section "root_command"
root_command echo "\$(id)"

