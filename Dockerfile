FROM jaohaohsuan/jnlp-slave:latest
MAINTAINER Henry Jao
WORKDIR /home/jenkins
ADD . ./
RUN set -x
RUN sbt 'project root' 'release'
RUN sbt 'project cluster' 'compile' 'test'
RUN sbt 'project frontend' 'compile' 'test' && set +x
