FROM jaohaohsuan/jnlp-slave:latest
MAINTAINER Henry Jao
COPY build.sbt ./
COPY project project/
COPY common common/
COPY protocol protocol/
COPY seed seed/
#RUN chown -R jenkins:jenkins /home/jenkins
RUN /usr/local/bin/sbt -v -sbt-dir /tmp/.sbt/0.13.11 -sbt-boot /tmp/.sbt/boot -ivy /tmp/.ivy2 -sbt-launch-dir /tmp/.sbt/launchers 'project seed' 'compile' && \
    alias sbt='/usr/local/bin/sbt -v -sbt-dir /tmp/.sbt/0.13.11 -sbt-boot /tmp/.sbt/boot -ivy /tmp/.ivy2 -sbt-launch-dir /tmp/.sbt/launchers'
