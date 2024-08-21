FROM rockylinux/rockylinux:9.1


RUN printf "\n\n ________ installing fio ________\n\n" && \
	dnf -y install stress-ng ; \
	mkdir --parents --verbose /csap-data ; \
    mkdir --parents --verbose /csap-utils ;


ENV parameters="--cpu 1" \
	SPACER="\n___________________________________\n\n"

RUN echo -e "$SPACER \
	parameters:  $parameters \n \
	path: $PATH \n \
	$SPACER"



WORKDIR "/csap-utils"
ADD launch-it.sh .
RUN chmod +x launch-it.sh

ENTRYPOINT [ \
	"/bin/sh",  \
	"-c", \
	"/csap-utils/launch-it.sh" \
]

WORKDIR "/csap-data"


#FROM centos:centos7

#
# demo-xxx-docker.csap.org/stress:latest
# 

#RUN printf "\n\n ________ installing core os tools ________\n\n" && \
#	yum -y install epel-release && \
#	yum clean all && \
#	yum -y install wget which unzip && \
#	yum -y update bind-license

#RUN printf "\n\n ***** installing core os tools ***\n\n" && \
#	yum -y install epel-release && \ 
#	yum -y update && \ 
#	yum clean all && \
#	yum -y install wget which unzip && \
#	yum -y update bind-license

#RUN yum -y install stress

#ENTRYPOINT [ \
#			"/bin/bash", \
#			"-c", \
#			"echo running as `id`; echo \"path is $PATH\"; stress --verbose" \
#]
#CMD sleep 300
# CMD /usr/bin/stress --cpu 1