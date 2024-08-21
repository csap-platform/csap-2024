#!/bin/bash

#
#  install bash on windows: lxrun /install
#

if test -d /usr/local/opt/coreutils/libexec/gnubin ; then 
	export PATH="/usr/local/opt/coreutils/libexec/gnubin:$PATH" ;
	echo "\n\n UPDATED PATH"
fi ;

testHost="${1:-csap-01.root}"

scriptName=$(basename $0) ;


echo "\n\nHOME: '$HOME', \n\n Working Directory: '$(pwd)' \n\n PATH: '$PATH' \n\n release: '$(uname -a 2>&1)'"

#cat $HOME/.ssh/known_hosts

if [ -e installer/csap-environment.sh ] ; then

	scriptDir=$(pwd)
	echo "installer/csap-environment.sh" ;
	
elif [ -e ../environment/csap-environment.sh ] ; then

	cd ..
	scriptDir=$(pwd) ;
	
	
	
	opensourceLocation="/mnt/c/dev/opensource" ;
	windowsHome="/mnt/c/Users/peter.nightingale"
	
	if  test -d $windowsHome/.ssh ; then
		echo "\n\nDesktop development using windows subsystem for linux, scriptDir: '$scriptDir'"
		
		if ! test -d $HOME/.ssh ; then
			cp --verbose --recursive --force $windowsHome/.ssh $HOME;
			chmod 700 $HOME/.ssh ; chmod 644 $HOME/.ssh/config ; chmod 600 ~/.ssh/id_rsa
		fi ;
		
		if ! test -d $HOME/opensource2 ; then
			ln -s  $windowsHome/opensource2 $HOME/opensource2
		fi
		
		if ! test -d $HOME/git ; then
			ln -s  $windowsHome/git $HOME/git 
		fi
		
	elif test -d $HOME/opensource2 ; then
		
		echo "\n\n Found $HOME/opensource2"
		
	else
		echo "\n\n WARNING: create '$windowsHome/.ssh', '$windowsHome/opensource2', and '$windowsHome/git'"
	fi ;
	 
	
#	if [ -f installer/ssh-config ] ; then
#		mkdir --parents --verbose $HOME/.ssh ;
#		cp --verbose --force installer/ssh-config $HOME/.ssh/config;
#		chmod 700 $HOME/.ssh ; chmod 644 $HOME/.ssh/config
#		ln -s  $opensourceLocation $HOME/opensource 
#	fi ;
	
else
	echo "Desktop development using git bash: '$scriptDir'"
	source $scriptDir/platform-bin/csap-environment.sh
fi

ENV_FUNCTIONS=$scriptDir/environment/functions ;
source $scriptDir/environment/csap-environment.sh ;

#testHost="centos1.root"
#testHost="csap-dev20.root"
#testHost=${1:-centos1.root} ;

print_with_head "current directory: '$(pwd)'"

print_with_head "listing of installer folder"
ls installer


#ssh nightingale-one.root ls

print_with_head "remote listing '$testHost' . Ensure ~/.ssh/config has alias added. Run remote-install.sh to see setup"
ssh $testHost 'ls *'

ssh $testHost 'ls * 2>&1'

exit

print_with_head "Cleaning up previous installs"
ssh $testHost rm -rvf installer opensource platform-bin

print_with_head "Copying latest installer"
scp -r installer $testHost:

scp -r $HOME/opensource/*.zip $testHost:

ssh $testHost ls -l 