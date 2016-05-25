FROM anapsix/alpine-java:jdk8
MAINTAINER Henry Jao

ENV HOME=/home/jenkins \ 
    DOCKER_BUCKET=get.docker.com \
    DOCKER_VERSION=1.11.1 \
    DOCKER_SHA256=893e3c6e89c0cd2c5f1e51ea41bc2dd97f5e791fcfa3cee28445df277836339d

WORKDIR /home/jenkins
COPY jenkins-slave /usr/local/bin/jenkins-slave
COPY build.sbt $HOME/
COPY project project/
COPY common $HOME/common
COPY protocol $HOME/protocol
COPY seed $HOME/seed
RUN set -x \
        && apk add --no-cache curl bash git openssh \
	&& curl -fSL "https://${DOCKER_BUCKET}/builds/Linux/x86_64/docker-$DOCKER_VERSION.tgz" -o docker.tgz \
	&& echo "${DOCKER_SHA256} *docker.tgz" | sha256sum -c - \
	&& tar -xzvf docker.tgz \
	&& mv docker/* /usr/local/bin/ \
	&& rmdir docker \
	&& rm docker.tgz \
	&& docker -v \
        && addgroup jenkins \
        && adduser -h $HOME -s /bin/bash -D -G jenkins jenkins \
        && addgroup -g 999 docker && adduser jenkins docker \
        && chown -R jenkins:jenkins /home/jenkins \
        && curl --create-dirs -sSLo /usr/share/jenkins/slave.jar http://repo.jenkins-ci.org/public/org/jenkins-ci/main/remoting/2.52/remoting-2.52.jar \
        && chmod 755 /usr/share/jenkins \
        && chmod 644 /usr/share/jenkins/slave.jar \
        && curl --create-dirs -sSLo /usr/share/jenkins/slave.jar http://repo.jenkins-ci.org/public/org/jenkins-ci/main/remoting/2.52/remoting-2.52.jar \
        && chmod 755 /usr/share/jenkins \
        && chmod 644 /usr/share/jenkins/slave.jar \
	&& curl -s https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt > /usr/local/bin/sbt \
        && chmod 0755 /usr/local/bin/sbt \
        && /usr/local/bin/sbt -v -sbt-dir /tmp/.sbt/0.13.11 -sbt-boot /tmp/.sbt/boot -ivy /tmp/.ivy2 -sbt-launch-dir /tmp/.sbt/launchers 'project seed' 'compile' \
        && rm -rf *

USER jenkins
VOLUME /home/jenkins
ENTRYPOINT ["jenkins-slave"]
