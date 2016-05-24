FROM alpine:3.3
MAINTAINER Henry Jao
ENV LANG C.UTF-8
RUN { \
echo '#!/bin/sh'; \
echo 'set -e'; \
echo; \
echo 'dirname "$(dirname "$(readlink -f "$(which javac || which java)")")"'; \
} > /usr/local/bin/docker-java-home \
&& chmod +x /usr/local/bin/docker-java-home

ENV JAVA_HOME /usr/lib/jvm/java-1.8-openjdk/jre
ENV PATH $PATH:$JAVA_HOME/bin
ENV JAVA_VERSION 8u92
ENV JAVA_ALPINE_VERSION 8.92.14-r0
RUN set -x \
  && apk add --no-cache openjdk8-jre="$JAVA_ALPINE_VERSION" bash curl git openssh ca-certificates \
  && [ "$JAVA_HOME" = "$(docker-java-home)" ]

# Install docker
ENV DOCKER_BUCKET get.docker.com
ENV DOCKER_VERSION 1.11.1
ENV DOCKER_SHA256 893e3c6e89c0cd2c5f1e51ea41bc2dd97f5e791fcfa3cee28445df277836339d

RUN set -x \
	&& curl -fSL "https://${DOCKER_BUCKET}/builds/Linux/x86_64/docker-$DOCKER_VERSION.tgz" -o docker.tgz \
	&& echo "${DOCKER_SHA256} *docker.tgz" | sha256sum -c - \
	&& tar -xzvf docker.tgz \
	&& mv docker/* /usr/local/bin/ \
	&& rmdir docker \
	&& rm docker.tgz \
	&& docker -v

ENV HOME /home/jenkins
RUN addgroup jenkins && adduser -h $HOME -s /bin/bash -D -G jenkins jenkins
RUN addgroup -g 999 docker && adduser jenkins docker

RUN curl --create-dirs -sSLo /usr/share/jenkins/slave.jar http://repo.jenkins-ci.org/public/org/jenkins-ci/main/remoting/2.52/remoting-2.52.jar \
  && chmod 755 /usr/share/jenkins \
  && chmod 644 /usr/share/jenkins/slave.jar

RUN curl -s https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt > /usr/local/bin/sbt && \
    chmod 0755 /usr/local/bin/sbt

COPY jenkins-slave /usr/local/bin/jenkins-slave
ADD project $HOME/project
ADD build.sbt $HOME/
ADD common $HOME/common
ADD protocol $HOME/protocol
ADD seed $HOME/seed
RUN chown -R jenkins:jenkins /home/jenkins
WORKDIR /home/jenkins
USER jenkins
RUN /usr/local/bin/sbt -v -sbt-dir /tmp/.sbt/0.13.11 -sbt-boot /tmp/.sbt/boot -ivy /tmp/.ivy2 -sbt-launch-dir /tmp/.sbt/launchers 'project seed' 'compile'

VOLUME /home/jenkins
ENTRYPOINT ["jenkins-slave"]
