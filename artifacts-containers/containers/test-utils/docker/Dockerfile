#
# use: docker run --rm -v $(pwd)/build:/transfer docker-dev-artifactory.yourcompany.com/csapplatform/test-utils:latest
#
FROM rockylinux/rockylinux:8

#ARG repoVersion=23.01
#ARG repoPath=artifactory/csap-release
#
##ARG repoVersion=2-SNAPSHOT
##ARG repoPath=artifactory/csap-snapshots
#

ARG dataFolder="/csap-data"

ARG testFolder="test-utils"
#ARG packageServer="http://devops-prod01.csap.org:8081/${repoPath}/org/csap/csap-host/${repoVersion}"
#testJarUrl="http://ec2-11-22-33-44.us-west-2.compute.amazonaws.com:8011/api/agent/installer"

ENV SPACER="\n_________________________________________________________________________________________________________\n\n" \
	dataFolder=$dataFolder \
	testFolder=$testFolder \
	parameters="-DcsapTestAllocKb=3 -DcsapTestAllocCountK=3 -DcsapTestInitAllocGb=1 -DcsapTestAllocJsonObjects=true  -Xms3G -Xmx3G -XX:MaxMetaspaceSize=192M -DcsapNoSecurity -Dspring.profiles.active=CSAP_LIFE,services-embedded,company -Dserver.port=9210"


RUN echo -e "$SPACER \
	dataFolder:  $dataFolder \n \
	testFolder:  $testFolder \n \
	path: $PATH \n \
	$SPACER"


RUN printf "\n\n ________ installing java 17 ________\n\n" && \
	dnf -y install java-17-openjdk-devel ; \
    printf "\n\n ________ creating install folders ________\n\n" && \
	mkdir --parents --verbose $dataFolder ;

WORKDIR $dataFolder

VOLUME /transfer


#RUN echo -e "$SPACER \
#	adding:  $packageServer/$testJar \n \
#	$SPACER"


ADD launch-it.sh .
RUN chmod +x launch-it.sh
ADD  $testFolder $dataFolder/$testFolder


EXPOSE 9210

ENTRYPOINT [ \
	"/bin/sh",  \
	"-c", \
	"/$dataFolder/launch-it.sh" \
]

#ENTRYPOINT [ \
#    "sh",  \
#    "-c", \
#    "echo -e $SPACER copying $dataFolder/$testFolder to volume /transfer $SPACER && \
#    cp --verbose --recursive $dataFolder/$testFolder /transfer" \
#]


