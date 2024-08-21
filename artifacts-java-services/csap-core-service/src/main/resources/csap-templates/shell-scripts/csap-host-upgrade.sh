# csap host upgrade, requires: csap-agent previously installed ;  runs install on 1 or more hosts using a specified definition

#
#   To run: update settings, then select 1 more hosts on csap commandrunner.
#

function settings() {

  testOnly=true;
  csapVersion="23.10"
  csapDistFolder=/perfshared/csap  ; # will be switched to /data/csap if all selected

  downloadFromRepoIfHostMatch="false"; # hostShortName, or all COPIES TO HOME instead of csapDistFolder
  repoCredentials="first.last:artToken"
  repoUrl="https://artifactory.yourcompany.com/artifactory/workday-experimental/org/csap/bin/csap-host-install"


  print_section "Settings"

  print_two_columns "csapVersion" "$csapVersion"
  print_two_columns "csapDistFolder" "$csapDistFolder"
  print_two_columns "downloadFromRepoIfHostMatch" "$downloadFromRepoIfHostMatch"
  print_two_columns "repoUrl" "$repoUrl"
  print_two_columns "testOnly" "$testOnly"

}

#
#  Implementation
#


function create_install_script_in_root() {

  local installScript=${1};

  local definitionHost=$(hostname --long) ; # defaults to current host

  local selfUpgradeScript="$HOME/csap-self-upgrade.sh"

  if test -f $selfUpgradeScript ; then
    rm --verbose --force $selfUpgradeScript ;
  fi

  # resolve variables
  cat >$selfUpgradeScript <<EOF
#!/bin/bash
source $CSAP_FOLDER/bin/csap-environment.sh
csapVersion=${csapVersion}
csapDistFolder=${csapDistFolder}

definitionUrl="http://${definitionHost}:8011/os/definitionZip"

EOF

  # NO variables resolved, wild card expansion
  cat >>$selfUpgradeScript <<'EOF'

print_section "Cleaning up previous installers"
cd /root; rm --recursive --force --verbose app*.zip csap*.zip *gradle* *play* *settings* installer ;

print_section "Copying installer"
cp --verbose ${csapDistFolder}/csap-host-install-${csapVersion}.zip .

print_section "Extracting installer"
unzip -j csap-host-*.zip csap-platform/packages/csap-package-linux.zip && unzip -qq csap-package-linux.zip installer/*


targetDefinition="/root/application.zip" ;
print_section  "Getting Definition $definitionUrl"
curl --request GET "$definitionUrl" --remote-name
mv --verbose definitionZip $targetDefinition

definitionHost="$(hostname --short)"

#
# -runServicePreKill will NOT stop csap.service - which will keep any spawned children running
#
./installer/install.sh -noPrompt -ignorePreflight \
    -runCleanUp \
    -installCsap "default" \
    -csapDefinition $targetDefinition &> csap-install.txt


EOF


  print_command "created  $selfUpgradeScript" "$(cat $selfUpgradeScript)"

  run_using_root rm --verbose --force $installScript
  run_using_root mv --verbose $selfUpgradeScript $installScript
  run_using_root chmod 755 $installScript



}

#
# to avoid self killing of installer, upgrade must be done using system service
#
function create_self_upgrade_service() {

  local installScript=${1};

  local selfUpgradeService="$HOME/csap-self-upgrade-service"

  cat &>$selfUpgradeService <<EOF
[Unit]
Description=CSAP Self Upgrade Service

[Service]
Type=simple
User=root
Group=root
TimeoutStartSec=300
Restart=no
#Restart=on-failure
RestartSec=30s
#ExecStartPre=
ExecStart=$installScript
SyslogIdentifier=csap-self-upgrade
#ExecStop=

[Install]
WantedBy=multi-user.target
EOF


  print_command "created  $selfUpgradeService" "$(cat $selfUpgradeService)"

  local linuxSystemServicePath="/etc/systemd/system/csap-self-upgrade.service"
  run_using_root rm --verbose --force $linuxSystemServicePath
  run_using_root mv --verbose $selfUpgradeService $linuxSystemServicePath
}

function performInstall() {

  local installScript="/root/csap-self-upgrade.sh"
  create_install_script_in_root $installScript
  create_self_upgrade_service $installScript

  local appendLogs=no ;
  local useRoot=yes
  local args="";

  local NOW=$(date +"%h-%d--%H-%M") ;
  local installerStartLogFile="/tmp/csap-host-install-start.txt" ;



  if $testOnly ; then
    print_section "Test Mode is '$testOnly', skipping install"
  else
    run_using_root systemctl start csap-self-upgrade.service ;
    #launch_background "$installScript" "$args" $installerStartLogFile $appendLogs $useRoot;
  fi ;


  print_section "Exiting"
  print_two_columns "installerLogFile" "/root/csap-install.txt" ;
  print_two_columns "upgrade service logs" "journalctl --unit csap-self-upgrade --since \"10 minutes ago\"" ;

  print_two_columns "Estimated time" "Basic install is < 1 minute."
  print_two_columns "Agent Url" "http://$(hostname --long):8011"
}


#
# Optional - grab latest installer
#

function getCsapFromArtifactory() {

  print_command "csapDistFolder $csapDistFolder" "$(ls $csapDistFolder)"

  installerUrl="$repoUrl/${csapVersion}/csap-host-install-${csapVersion}.zip";

  print_section  curl --user "$repoCredentials" --request GET "$installerUrl" --remote-name

  if [[ $repoCredentials == *"artToken"* ]] || [[ $repoCredentials == *"first"* ]] ; then
    print_error "update the credentials"
    exit 99 ;
  fi ;

  cd $csapDistFolder;

  if test -f csap-host-install-${csapVersion}.zip ; then
    backup_file csap-host-install-${csapVersion}.zip ;
  fi ;

  curl --user "$repoCredentials" --request GET "$installerUrl" --remote-name

}

function runIt() {

  settings

  if [[ "$downloadFromRepoIfHostMatch" == "all" ]] ; then

    csapDistFolder=/data/csap
    print_two_columns "csapDistFolder updated" "$csapDistFolder"

  fi ;

  if [[ "$(hostname --long)" == *"$downloadFromRepoIfHostMatch"* ]] \
    || [[ "$downloadFromRepoIfHostMatch" == "all" ]] ; then
    getCsapFromArtifactory
  fi ;

  performInstall

}

runIt
