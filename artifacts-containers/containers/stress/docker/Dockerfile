FROM rockylinux/rockylinux:8
#FROM rockylinux/rockylinux:9.1


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

