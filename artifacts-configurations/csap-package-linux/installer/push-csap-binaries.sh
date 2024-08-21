#!/usr/bin/env bash

source ./platform-setup.sh

setupEnvironment ;

print_section "Starting push"

csapBinariesFolder="$csapHome/thirdPartyDist" ;

print_command "binaries" "$(ls -d $csapBinariesFolder/*secondary)"


function publishUsingMaven() {

  VERSION=${1:-3.6.1}
  FILE=${2:-apache-maven-3.6.1-bin.zip}

  GROUP_ID="bin"
  ARTIFACT_ID="maven"
  TYPE="zip"

  REPO_ID="csap-release-repo"
  REPO_URL="http://devops-prod01.lab.somecompany3.net:8081/artifactory/csap-release/"

  # Note: this will require version to have SNAPSHOT APPENDED
  # VERSION="$VERSION-SNAPSHOT"
  # REPO_ID="csap-snapshot-repo"
  # REPO_URL="http://devops-prod01.lab.somecompany3.net:8081/artifactory/csap-snapshots"


  mavenSettingsFile="$HOME/.m2/settings.xml"

  mvn -s $mavenSettingsFile deploy:deploy-file \
    -DgroupId=$GROUP_ID \
    -DartifactId=$ARTIFACT_ID \
    -Dversion=$VERSION \
    -Dpackaging=$TYPE \
    -Dfile=$FILE \
    -DrepositoryId=$REPO_ID \
    -Durl=$REPO_URL

}

# avoid matching wild cards in for loops
shopt -s nullglob
for binFolder in $csapBinariesFolder/*secondary ; do

  print_separator "processing: $binFolder"

  for binFile in $binFolder/*.zip ; do
    print_two_columns "binary" "$binFile"
  done ;


done ;

# unset it now
shopt -u nullglob
