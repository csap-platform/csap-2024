#!/bin/bash


#
#  used when csap ui stores files to fs
#

if (( $# < 6 )) ; then 
	echo  "params: csapPlatformFolder tempFileWithUserEdits fileToBeUpdated linuxUserid csapUserid keepPermissions"
	exit ;
fi ;

csapPlatformFolder="$1";
tempFileWithUserEdits="$2" ;
fileToBeUpdated="$3"
linuxUserid="$4" ;
linuxGroup="$linuxUserid"
csapUserid="$5"
isKeepPermissions="$6"

function setupVariables() {

	csapPlatformEnvironment="$csapPlatformFolder/bin/csap-environment.sh" ;
	csapPlatformDefinition="$csapPlatformFolder/definition" ;

	if [ -e  $csapPlatformEnvironment ] ; then 
		source $csapPlatformEnvironment ;
	else
		echo "Exiting - unable to locate csapPlatformEnvironment: '$csapPlatformEnvironment'" ;
		exit ;
	fi

	isUpdateToDefinitionFolder=false;
	if [[ "$fileToBeUpdated" == $csapPlatformDefinition/* ]] ; then
		isUpdateToDefinitionFolder=true;
	fi ;

	# params: tempLocation, fileToBeUpdated, targetUnixOwner, linuxUserid


	print_section "Updating $fileToBeUpdated"
	print_two_columns "user" "$csapUserid"
	print_two_columns "source" "$tempFileWithUserEdits"
	print_two_columns "linuxUserid" "$linuxUserid"
	print_two_columns "linuxGroup" "$linuxGroup"

	if [[ $isKeepPermissions != "true" ]] ; then
		print_two_columns "dos2unix" "ensuring uploaded file is using linux line format"
		convertOutput=$(dos2unix $tempFileWithUserEdits 2>&1)
	fi ;

	if  [ "$USER" == "root" ]  ; then
		chown -R $linuxUserid $tempFileWithUserEdits
		chgrp -R $linuxGroup $tempFileWithUserEdits
	else 
		print_two_columns "mode" "running in non root mode"
	fi ; 
}
setupVariables

#
# create .orig it if does not exist, keep 1 backup with -userid
#
function createBackups() {

	chmod 755 $tempFileWithUserEdits
	# NOW=$(date +"%h-%d-%I-%M-%S")
	local fileNameOnly=$( basename $fileToBeUpdated ) ;
	local fileExtension=${fileNameOnly##*.}
#	if [ "$fileExtension" != "" ] ; then  fileExtension=".$fileExtension" ; fi ;
	if [ "$fileExtension" != "$fileNameOnly" ] ; then
	  fileExtension=".$fileExtension" ;
	else
	  fileExtension="" ;
  fi ;

	local originalFile="$fileToBeUpdated-orig$fileExtension"

	backupFileWithUserIdExtension="$fileToBeUpdated-$csapUserid$fileExtension"

	if test -e "$fileToBeUpdated"  ; then
		
		if $isUpdateToDefinitionFolder ; then
			print_two_columns "definition folder" "skipping backups - use csap editor to check in changes" ;
		
		else

			if ! test -f $originalFile ; then
				print_two_columns "original file" "backed up to: '$originalFile'" ;
				\cp --force --preserve "$fileToBeUpdated" "$originalFile" ;

				if  [ "$USER" == "root" ] ; then
					chmod --reference="$fileToBeUpdated" "$originalFile"
					chown --reference="$fileToBeUpdated" "$originalFile"
				fi ;
			fi ;

			print_two_columns "user backups" "previous version: '$backupFileWithUserIdExtension'" ;
			\cp --force --preserve "$fileToBeUpdated" "$backupFileWithUserIdExtension" ;
			
			if  [ "$USER" == "root" ] ; then
				chmod --reference="$fileToBeUpdated" "$backupFileWithUserIdExtension"
				chown --reference="$fileToBeUpdated" "$backupFileWithUserIdExtension"
			fi ;
		fi
		
	fi 

}
createBackups

function performSave() {

	if [[ $isKeepPermissions == "true" ]] ; then
		print_two_columns "permissions" "keep permissions is true, using 'cat > existing file'" ;
		cat "$tempFileWithUserEdits" > "$fileToBeUpdated" ;

	else

		print_two_columns "permissions" "keep permissions is false, using 'cp --force' to overwrite" ;
		cp --force "$tempFileWithUserEdits" "$fileToBeUpdated" 
		
		if  [ "$USER" == "root" ]  ; then

			print_two_columns "updated file metadata" "file chown/chmod cloned from original file" ;

			chmod --reference="$backupFileWithUserIdExtension" "$fileToBeUpdated" 
			chown --reference="$backupFileWithUserIdExtension" "$fileToBeUpdated" 

			# do NOT allow modifications to file via editor ui
			# if [ "$linuxUserid" != "keep" ] ; then
			# 	print_two_columns "resolved: chown" "$linuxUserid"
			# 	print_two_columns "resolved: chgrp" "$linuxGroup"
			# fi ;
		fi ;
		
	fi ;

}

performSave

#
# Cleaning up the temp file used to copy in changes
#
\rm --recursive --force "$tempFileWithUserEdits" ;



