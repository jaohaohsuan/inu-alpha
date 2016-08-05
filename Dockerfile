FROM jaohaohsuan/jnlp-slave:latest
MAINTAINER Henry Jao
WORKDIR /home/jenkins
ADD . ./
RUN sbt 'project root' 'release' 'clean'
RUN sbt 'project cluster' 'compile' 'clean' 'test' 'clean'
RUN sbt 'project frontend' 'compile' 'clean'
