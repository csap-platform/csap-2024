# chef examples, run chef commands


#
#  ref. https://docs.chef.io/ctl_chef_client/ , https://docs.chef.io/workstation/knife/
#


run_and_format cat /etc/motd

run_and_format_root cat /var/run/chef/resources.txt

run_knife role list ;

run_knife cookbook list ;

run_knife data bag list

run_knife node list

