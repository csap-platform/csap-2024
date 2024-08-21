#
# ref https://springframework.guru/running-spring-boot-in-a-docker-container/
# 


FROM centos

ENV JAVA_HOME="/java-local/jdk1.8.0_121"
ENV CLASS_PATH .
ENV PATH="${JAVA_HOME}/bin:${PATH}

ENV PARENT_HOST="$(hostname)"
# Upgrading system
#RUN yum -y upgrade
RUN yum -y install wget which
#RUN yum -y install wget

# Downloading & Config Java 8
#RUN mkdir -p /java ; cd /java
#RUN wget -nv --no-cookies --no-check-certificate "http://csaptools.yourcompany.com/java/jdk-8u121-linux-x64.tar.gz" -O /java/jdk.tar.gz
#RUN tar -xzf /tmp/jdk.tar.gz
#RUN rm -rf /java/jdk.tar.gz
# for debugging - copy resources into local build context target/docker/build/
ADD jdk-8u121-linux-x64.tar.gz /java-local


VOLUME /tmp
ADD /maven/BootEnterprise-1.0.31-SNAPSHOT.jar csapTest.jar

RUN sh -c 'touch /csapTest.jar'

# http 
EXPOSE 8080
#jmx 
#EXPOSE 8086

#
# Java RMI requires explicit host name - Recommend a string substition at lauch  
#
#ENTRYPOINT [ \
#	"java", "-Xms256M", "-Xmx256M", \
#	"-Dspring.profiles.active=embedded", \
#	"-Djava.security.egd=file:/dev/./urandom", \
#	"-Djava.rmi.server.hostname=_HOST_NAME_",  "-Dcom.sun.management.jmxremote.port=8086", "-Dcom.sun.management.jmxremote.rmi.port=8086", \
#	"-Dcom.sun.management.jmxremote.local.only=false", "-Dcom.sun.management.jmxremote.authenticate=false", "-Dcom.sun.management.jmxremote.ssl=false", \
#	"-jar","/csapTest.jar" \
# ]

ENTRYPOINT [ \
	"java", "-Xms256M", "-Xmx256M", \
	"-Dspring.profiles.active=embedded", \
	"-DcsapJava8", \
	"-DcsapJmxPort=8086", \
	"-jar","/csapTest.jar" \
]



