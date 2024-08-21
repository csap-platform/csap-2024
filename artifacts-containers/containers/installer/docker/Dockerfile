#
#
#
FROM rockylinux/rockylinux:8

ARG repoVersion=IMAGE_VERSION
#ARG repoVersion=23.01
#ARG repoPath=artifactory/csap-release
#
##ARG repoVersion=2-SNAPSHOT
##ARG repoPath=artifactory/csap-snapshots
#
ARG csapZip="csap-host-install-${repoVersion}.zip"
#ARG packageServer="http://devops-prod01.csap.org:8081/${repoPath}/org/csap/csap-host/${repoVersion}"
#csapZipUrl="http://ec2-11-22-33-44.us-west-2.compute.amazonaws.com:8011/api/agent/installer"

ENV SPACER="\n_________________________________________________________________________________________________________\n\n" \
	csapZip=$csapZip

WORKDIR /root

VOLUME /transfer


#RUN echo -e "$SPACER \
#	adding:  $packageServer/$csapZip \n \
#	$SPACER"
	
ADD $csapZip $csapZip

#ADD $packageServer/$csapZip $csapZip


ENTRYPOINT [ \
    "sh",  \
    "-c", \
    "echo -e $SPACER copying $csapZip to volume /transfer $SPACER && \
    cp --verbose /root/$csapZip /transfer" \
]


