FROM rockylinux/rockylinux:8
#FROM ubuntu:latest



#
# multiarch:
# docker buildx create --name fio-builder --bootstrap --use
#docker buildx build --push \
#  --platform linux/arm64/v8,linux/amd64 \
#  --tag  docker-dev-artifactory.yourcompany.com/csapplatform/sysbench:latest \
#  .
#



RUN printf "\n\n ________ installing sysbench ________\n\n" && \
	dnf -y install epel-release ; \
	dnf -y install sysbench ; \
	mkdir --parents --verbose /csap-data ; \
    mkdir --parents --verbose /csap-utils ;


ENV parameters="cpu --time=10 --threads=2 --cpu-max-prime=100000 run" \
    location="/csap-data" \
	SPACER="\n___________________________________\n\n"

RUN echo -e "$SPACER \
	parameters:  $parameters \n \
	location:  $location \n \
	path: $PATH \n \
	$SPACER"



WORKDIR "/csap-utils"
ADD launch-it.sh .
RUN chmod +x launch-it.sh

#ENTRYPOINT [ \
#	"/bin/sh",  \
#	"-c", \
#	"/csap-utils/launch-it.sh" \
#]

VOLUME /csap-data
WORKDIR "/csap-data"

#ENTRYPOINT [ \
#	"fio",  \
#	"-version" \
#]
CMD sysbench --version
