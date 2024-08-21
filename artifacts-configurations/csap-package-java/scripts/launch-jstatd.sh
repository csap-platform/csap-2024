source $CSAP_FOLDER/bin/csap-environment.sh

print_section "jstatd launcher"

run_and_format_root killall jstatd

print_two_columns "jstatd" "$(which jstatd)"


print_two_columns "csapLogDir" "$csapLogDir $( echo $( mkdir --parents --verbose $csapLogDir )  )"

policyFile="$csapLogDir/tools.policy"
print_two_columns "policyFile" "$policyFile"

#
#  csap java 17 - note this will be not be needed in future releases
#
cat >$policyFile<<EOF
grant codebase "jrt:/jdk.jstatd" {
   permission java.security.AllPermission;
};

grant codebase "jrt:/jdk.internal.jvmstat" {
   permission java.security.AllPermission;
};
EOF

#
# Java 8
#
#cat >$policyFile<<EOF
#grant codebase "file:$JAVA_HOME/lib/tools.jar" {
#   permission java.security.AllPermission;
#};
#EOF

run_and_format cat $policyFile


#jstatd -J-Djava.security.policy=/data/csap/java/tools.policy
launch_background "jstatd" "-J-Djava.security.policy=$policyFile" "$csapLogDir/jstatd.log"

#jstatd -J-Djava.security.policy=$policyFile