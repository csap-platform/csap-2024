FROM rockylinux/rockylinux:8
#FROM ubuntu:latest



#
# multiarch:
# docker buildx create --name fio-builder --bootstrap --use
#docker buildx build --push \
#  --platform linux/arm64/v8,linux/amd64 \
#  --tag  docker-dev-artifactory.yourcompany.com/csapplatform/fio:latest \
#  .
#



RUN printf "\n\n ________ installing fio ________\n\n" && \
	dnf -y install fio ; \
	mkdir --parents --verbose /csap-data ; \
    mkdir --parents --verbose /csap-utils ;


ENV parameters="--name=random-write --time_based --runtime=10 --bs=4k --size=1g  --ioengine=posixaio --rw=randwrite  --numjobs=1  --iodepth=1  --end_fsync=1" \
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

ENTRYPOINT [ \
	"/bin/sh",  \
	"-c", \
	"/csap-utils/launch-it.sh" \
]

VOLUME /csap-data
WORKDIR "/csap-data"

#ENTRYPOINT [ \
#	"fio",  \
#	"-version" \
#]
#CMD /usr/bin/stress --cpu 1
