# ssh permissions, Update the ssh home permissions of a user

cd _file_


print_two_columns "host" "$(hostname --long)"
print_two_columns "user" "$(whoami)"
print_two_columns "linux uptime" "$(uptime | xargs)"

doFix=false ; # switch to true to update permissions

sshUserToFixPermissions="UPDATE_ME"

if [[ "$sshUserToFixPermissions" == "UPDATE_ME" ]] ; then

  print_error "Update the userid"
  exit;
fi;

if $doFix ; then
  chown $sshUserToFixPermissions:$sshUserToFixPermissions /home/$sshUserToFixPermissions -R
  chown $sshUserToFixPermissions:$sshUserToFixPermissions /home/$sshUserToFixPermissions.ssh -R

  chmod -R 400 /home/$sshUserToFixPermissions/.ssh
  chmod 700 /home/$sshUserToFixPermissions/.ssh
  chmod 700 /home/$sshUserToFixPermissions
fi ;


print_command "ls -ald /home/$sshUserToFixPermissions" "$(ls -ald /home/$sshUserToFixPermissions)"
print_command "ls -al /home/$sshUserToFixPermissions/.ssh" "$(ls -al /home/$sshUserToFixPermissions/.ssh)"
print_command "ls -ald /home/$sshUserToFixPermissions/.ssh" "$(ls -ald /home/$sshUserToFixPermissions/.ssh)"



