FROM jaohaohsuan/jnlp-slave:latest
MAINTAINER Henry Jao
WORKDIR /home/jenkins
ADD . ./
RUN sbt 'project root' 'release'
RUN sbt 'project cluster' 'compile' 'test' 'docker:stage'
RUN sbt 'project frontend' 'compile' 'test' 'docker:stage'
