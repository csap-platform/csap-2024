#!/bin/sh


scriptDir=$(dirname $0)

source $scriptDir/../csap-desktop.sh
batTheme="${batDarkTheme}"

unset JAVA_HOME
print_section "Running '$(trimPath $0 3  )'"

witch remote-junit
ggfilter junit-distributor-runner checkstyleMain

ggswitch wcsap
ggenv
