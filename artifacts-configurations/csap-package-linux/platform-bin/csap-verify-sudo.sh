#!/usr/bin/env bash

source $CSAP_FOLDER/bin/csap-environment.sh

if sudo --non-interactive $CSAP_FOLDER/bin/csap-run-as-root.sh ; then 
    print_if_debug "sudo enabled" ; 
else 
    print_error "Failed to get a no pass sudo session - verify installation"

    print_command \
        "try" \
        "$(echo sudo $csapPlatformWorking/csap-package-linux/installer/install-csap-sudo.sh csap $CSAP_FOLDER/bin)"
fi

print_command \
    "csap run_using_root includes verification" \
    "$(run_using_csap_root ls)"

